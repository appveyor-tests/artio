/*
 * Copyright 2015 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.fix_gateway.replication;

import uk.co.real_logic.aeron.Aeron;
import uk.co.real_logic.aeron.Publication;
import uk.co.real_logic.aeron.driver.MediaDriver;
import uk.co.real_logic.agrona.CloseHelper;
import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.collections.IntHashSet;
import uk.co.real_logic.agrona.concurrent.AtomicCounter;
import uk.co.real_logic.fix_gateway.engine.logger.ArchiveMetaData;
import uk.co.real_logic.fix_gateway.engine.logger.ArchiveReader;
import uk.co.real_logic.fix_gateway.engine.logger.Archiver;

import static org.mockito.Mockito.mock;
import static uk.co.real_logic.aeron.CommonContext.AERON_DIR_PROP_DEFAULT;
import static uk.co.real_logic.aeron.driver.ThreadingMode.SHARED;
import static uk.co.real_logic.fix_gateway.CommonConfiguration.backoffIdleStrategy;
import static uk.co.real_logic.fix_gateway.replication.AbstractReplicationTest.*;

public class NodeRunner implements AutoCloseable, Role
{
    public static final long NOT_LEADER = -3;

    public static final long TIMEOUT_IN_MS = 1000;
    public static final String AERON_GROUP = "aeron:udp?group=224.0.1.1:40456";

    private final SwitchableLossGenerator lossGenerator = new SwitchableLossGenerator();

    private final MediaDriver mediaDriver;
    private final Aeron aeron;
    private final RaftNode raftNode;
    private final Publication dataPublication;

    private long replicatedPosition = -1;
    private long timeInMs = 0;

    public NodeRunner(final int nodeId, final int... otherNodes)
    {
        final MediaDriver.Context context = new MediaDriver.Context();
        context
            .threadingMode(SHARED)
            .controlLossGenerator(lossGenerator)
            .dataLossGenerator(lossGenerator)
            .dirsDeleteOnStart(true)
            .aeronDirectoryName(AERON_DIR_PROP_DEFAULT + nodeId);

        final IntHashSet otherNodeIds = new IntHashSet(40, -1);
        for (final int node : otherNodes)
        {
            otherNodeIds.add(node);
        }

        mediaDriver = MediaDriver.launch(context);
        final Aeron.Context clientContext = new Aeron.Context();
        clientContext.aeronDirectoryName(context.aeronDirectoryName());
        aeron = Aeron.connect(clientContext);

        dataPublication = aeron.addPublication(AERON_GROUP, DATA);

        final StreamIdentifier dataStream = new StreamIdentifier(AERON_GROUP, DATA);
        final ArchiveMetaData metaData = AbstractReplicationTest.archiveMetaData((short) nodeId);
        final ArchiveReader archiveReader = new ArchiveReader(
            metaData, LOGGER_CACHE_CAPACITY, dataStream);
        final Archiver archiver = new Archiver(metaData, LOGGER_CACHE_CAPACITY, dataStream);

        final RaftNodeConfiguration configuration = new RaftNodeConfiguration()
            .nodeId((short) nodeId)
            .aeron(aeron)
            .otherNodes(otherNodeIds)
            .timeoutIntervalInMs(TIMEOUT_IN_MS)
            .acknowledgementStrategy(new EntireClusterAcknowledgementStrategy())
            .fragmentHandler((buffer, offset, length, header) -> replicatedPosition = offset + length)
            .failCounter(mock(AtomicCounter.class))
            .maxClaimAttempts(100_000)
            .acknowledgementStream(new StreamIdentifier(AERON_GROUP, ACKNOWLEDGEMENT))
            .controlStream(new StreamIdentifier(AERON_GROUP, CONTROL))
            .dataStream(dataStream)
            .idleStrategy(backoffIdleStrategy())
            .leaderSessionId(dataPublication.sessionId())
            .archiver(archiver)
            .archiveReader(archiveReader);

        raftNode = new RaftNode(configuration, timeInMs);
    }

    public int poll(final int fragmentLimit, final long timeInMs)
    {
        // NB: ignores other time
        return raftNode.poll(fragmentLimit, this.timeInMs);
    }

    public void closeStreams()
    {
        raftNode.closeStreams();
    }

    public void advanceClock(final long delta)
    {
        timeInMs += delta;
    }

    public void dropFrames(final boolean dropFrames)
    {
        lossGenerator.dropFrames(dropFrames);
    }

    public boolean isLeader()
    {
        return raftNode.roleIsLeader();
    }

    public RaftNode replicator()
    {
        return raftNode;
    }

    public long replicatedPosition()
    {
        return replicatedPosition;
    }

    public long offer(final DirectBuffer buffer, final int offset, final int length)
    {
        if (!raftNode.roleIsLeader())
        {
            return NOT_LEADER;
        }

        return dataPublication.offer(buffer, offset, length);
    }


    public void close()
    {
        CloseHelper.close(aeron);
        CloseHelper.close(mediaDriver);
    }
}
