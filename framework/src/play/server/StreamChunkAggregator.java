package play.server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;

import static org.jboss.netty.channel.Channels.*;
import static org.jboss.netty.handler.codec.http.HttpHeaders.*;


import org.apache.commons.io.IOUtils;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMessage;
import  org.jboss.netty.util.CharsetUtil;

import play.Play;

public class StreamChunkAggregator extends SimpleChannelUpstreamHandler {

    private volatile HttpMessage currentMessage;
    private volatile OutputStream out;
    private final static int maxContentLength = Integer.valueOf(Play.configuration.getProperty("play.netty.maxContentLength", "-1"));
    private volatile File file;

    private static final ChannelBuffer CONTINUE = ChannelBuffers.copiedBuffer("HTTP/1.1 100 Continue\r\n\r\n", CharsetUtil.US_ASCII);
    
    /**
     * Creates a new instance.
     */
    public StreamChunkAggregator() { }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        Object msg = e.getMessage();
        if (!(msg instanceof HttpMessage) && !(msg instanceof HttpChunk)) {
            ctx.sendUpstream(e);
            return;
        }

        HttpMessage currentMessage = this.currentMessage;
        File localFile = this.file;
        if (currentMessage == null) {
            HttpMessage m = (HttpMessage) msg;
            
            if (is100ContinueExpected(m)) {
                write(ctx, succeededFuture(ctx.getChannel()), CONTINUE.duplicate());
            }
            
            if (m.isChunked()) {
                final String localName = UUID.randomUUID().toString();
                // A chunked message - remove 'Transfer-Encoding' header,
                // initialize the cumulative buffer, and wait for incoming chunks.
                List<String> encodings = m.headers().getAll(HttpHeaders.Names.TRANSFER_ENCODING);
                encodings.remove(HttpHeaders.Values.CHUNKED);
                if (encodings.isEmpty()) {
                    m.headers().remove(HttpHeaders.Names.TRANSFER_ENCODING);
                }
                this.currentMessage = m;
                this.file = new File(Play.tmpDir, localName);
                this.out = new FileOutputStream(file, true);
            } else {
                // Not a chunked message - pass through.
                ctx.sendUpstream(e);
            }
        } else {
            // TODO: If less that threshold then in memory
            // Merge the received chunk into the content of the current message.
            final HttpChunk chunk = (HttpChunk) msg;
            if (maxContentLength != -1 && (localFile.length() > (maxContentLength - chunk.getContent().readableBytes()))) {
                currentMessage.headers().set(HttpHeaders.Names.WARNING, "play.netty.content.length.exceeded");
            } else {
                IOUtils.copyLarge(new ChannelBufferInputStream(chunk.getContent()), this.out);

                if (chunk.isLast()) {
                    this.out.flush();
                    this.out.close();

                    currentMessage.headers().set(
                            HttpHeaders.Names.CONTENT_LENGTH,
                            String.valueOf(localFile.length()));

                    currentMessage.setContent(new FileChannelBuffer(localFile));
                    this.out = null;
                    this.currentMessage = null;
		            this.file.delete();
                    this.file = null;
                    Channels.fireMessageReceived(ctx, currentMessage, e.getRemoteAddress());
                }
            }
        }

    }
}

