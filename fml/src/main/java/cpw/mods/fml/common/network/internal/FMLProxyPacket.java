package cpw.mods.fml.common.network.internal;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import org.apache.logging.log4j.Level;
import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import com.google.common.collect.Multisets;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.network.FMLNetworkException;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.handshake.NetworkDispatcher;
import cpw.mods.fml.relauncher.Side;

public class FMLProxyPacket extends Packet {
    final String channel;
    private Side target;
    private final ByteBuf payload;
    private INetHandler netHandler;
    private NetworkDispatcher dispatcher;
    private static Multiset<String> badPackets = ConcurrentHashMultiset.create();
    private static int packetCountWarning = Integer.getInteger("fml.badPacketCounter", 100);
    private FMLProxyPacket(byte[] payload, String channel)
    {
        this(Unpooled.wrappedBuffer(payload), channel);
    }

    public FMLProxyPacket(S3FPacketCustomPayload original)
    {
        this(original.func_149168_d(), original.func_149169_c());
        this.target = Side.CLIENT;
    }

    public FMLProxyPacket(C17PacketCustomPayload original)
    {
        this(original.func_149558_e(), original.func_149559_c());
        this.target = Side.SERVER;
    }

    public FMLProxyPacket(ByteBuf payload, String channel)
    {
        this.channel = channel;
        this.payload = payload;
    }
    @Override
    public void readPacketData(PacketBuffer packetbuffer) throws IOException
    {
        // NOOP - we are not built this way
    }

    @Override
    public void writePacketData(PacketBuffer packetbuffer) throws IOException
    {
        // NOOP - we are not built this way
    }

    @Override
    public void processPacket(INetHandler inethandler)
    {
        this.netHandler = inethandler;
        EmbeddedChannel internalChannel = NetworkRegistry.INSTANCE.getChannel(this.channel, this.target);
        if (internalChannel != null)
        {
            internalChannel.attr(NetworkRegistry.NET_HANDLER).set(this.netHandler);
            try
            {
                if (internalChannel.writeInbound(this))
                {
                    badPackets.add(this.channel);
                    if (badPackets.size() % packetCountWarning == 0)
                    {
                        FMLLog.severe("Detected ongoing potential memory leak. %d packets have leaked. Top offenders", badPackets.size());
                        int i = 0;
                        for (Entry<String> s  : Multisets.copyHighestCountFirst(badPackets).entrySet())
                        {
                            if (i++ > 10) break;
                            FMLLog.severe("\t %s : %d", s.getElement(), s.getCount());
                        }
                    }
                }
                internalChannel.inboundMessages().clear();
            }
            catch (FMLNetworkException ne)
            {
                FMLLog.log(Level.ERROR, ne, "There was a network exception handling a packet on channel %s", channel);
                dispatcher.rejectHandshake(ne.getMessage());
            }
            catch (Throwable t)
            {
                FMLLog.log(Level.ERROR, t, "There was a critical exception handling a packet on channel %s", channel);
                dispatcher.rejectHandshake("A fatal error has occured, this connection is terminated");
            }
        }
    }

    public String channel()
    {
        return channel;
    }
    public ByteBuf payload()
    {
        return payload;
    }
    public INetHandler handler()
    {
        return netHandler;
    }
    public Packet toC17Packet()
    {
        return new C17PacketCustomPayload(channel, payload.array());
    }

    public Packet toS3FPacket()
    {
        return new S3FPacketCustomPayload(channel, payload.array());
    }

    public void setTarget(Side target)
    {
        this.target = target;
    }

    public void setDispatcher(NetworkDispatcher networkDispatcher)
    {
        this.dispatcher = networkDispatcher;
    }

    public NetworkManager getOrigin()
    {
        return this.dispatcher != null ? this.dispatcher.manager : null;
    }

    public NetworkDispatcher getDispatcher()
    {
        return this.dispatcher;
    }

    public Side getTarget()
    {
        return target;
    }
}
