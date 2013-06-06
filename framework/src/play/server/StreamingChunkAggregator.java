package play.server;

import static org.jboss.netty.channel.Channels.succeededFuture;
import static org.jboss.netty.channel.Channels.write;
import static org.jboss.netty.handler.codec.http.HttpHeaders.is100ContinueExpected;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.io.IOUtils;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.util.CharsetUtil;

import play.Logger;

public class StreamingChunkAggregator extends SimpleChannelUpstreamHandler {

    private volatile HttpMessage currentMessage;
    private volatile ChunkedInputStream inputStream;
    private volatile boolean messageFired = false;
    
    private static final ChannelBuffer CONTINUE = ChannelBuffers.copiedBuffer("HTTP/1.1 100 Continue\r\n\r\n", CharsetUtil.US_ASCII);
    
    /**
     * Creates a new instance.
     */
    public StreamingChunkAggregator() {
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) throws Exception {
        Object msg = e.getMessage();
        if (!(msg instanceof HttpMessage) && !(msg instanceof HttpChunk)) {
            ctx.sendUpstream(e);
            return;
        }

        final HttpMessage currentMessage = this.currentMessage;
        if (currentMessage == null) {
            HttpMessage m = (HttpMessage) msg;
            
            if (is100ContinueExpected(m)) {
                write(ctx, succeededFuture(ctx.getChannel()), CONTINUE.duplicate());
            }
            
            if (m.isChunked()) { // A chunked message we can process                
                // A chunked message - remove 'Transfer-Encoding' header,
                // initialize the cumulative buffer, and wait for incoming chunks.
                List<String> encodings = m.getHeaders(HttpHeaders.Names.TRANSFER_ENCODING);
                encodings.remove(HttpHeaders.Values.CHUNKED);
                if (encodings.isEmpty()) {
                    m.removeHeader(HttpHeaders.Names.TRANSFER_ENCODING);
                }                
                if(Logger.isDebugEnabled()) Logger.debug("Setup CHUNKED Message");
                inputStream = new ChunkedInputStream();
                this.currentMessage = m;
                if(Logger.isDebugEnabled()) Logger.debug("this.currentMessage="+m);
            } else {
                // Not a chunked message - pass through.
                ctx.sendUpstream(e);
            }
        } else {
            if(Logger.isDebugEnabled()) Logger.debug("Appending Chunk content");
            final HttpChunk chunk = (HttpChunk) msg;
            if(! inputStream.isClosed() ){
                inputStream.addInputStream(new ByteArrayInputStream(IOUtils.toByteArray(new ChannelBufferInputStream(chunk.getContent()))));
                if(Logger.isDebugEnabled()) Logger.debug("Appended Chunk content "+inputStream.getInputStreamQueueSize());
                if (!messageFired) {
                    messageFired = true;
                    currentMessage.setContent(new InputStreamChannelBuffer(inputStream));
                    if(Logger.isDebugEnabled()) Logger.debug("Firing initial chunk of inputstream");                                        
                    Executors.newSingleThreadExecutor().submit(new Runnable() {
                        public void run() {
                            Channels.fireMessageReceived(ctx, currentMessage, e.getRemoteAddress());
                        }
                    });          
                    
                }                
            }
            if (chunk.isLast()) {
                if(Logger.isDebugEnabled()) Logger.debug("Added final inputstream");
                inputStream.lastInputStreamAdded();
                this.inputStream = null;
                this.currentMessage = null;    
                this.messageFired = false;
            }            
        }

    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        super.channelDisconnected(ctx, e);
        killPending();
    }

    private void killPending() {
        if(inputStream != null){
            try {
                this.inputStream.close();
            } catch (IOException ioe) {
                Logger.warn(ioe,"Unable to properly kill pending.");
            }
            this.inputStream = null;   
        }        
        this.currentMessage = null;    
        this.messageFired = false;        
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        super.channelClosed(ctx, e);
        killPending();
    }
    
}

