package play.server.ssl;

import javax.net.ssl.SSLEngine;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.handler.codec.http.HttpContentCompressor;
import org.jboss.netty.handler.codec.http.HttpContentDecompressor;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;

import play.Play;
import play.server.FlashPolicyHandler;
import play.server.StreamChunkAggregator;
import play.server.StreamingChunkAggregator;

import static org.jboss.netty.channel.Channels.pipeline;


public class SslHttpServerPipelineFactory implements ChannelPipelineFactory {

    public ChannelPipeline getPipeline() throws Exception {

        Integer max = Integer.valueOf(Play.configuration.getProperty("play.netty.maxContentLength", "-1"));
        String mode = Play.configuration.getProperty("play.netty.clientAuth", "none");
        Boolean bufferedAggregator = Boolean.parseBoolean(Play.configuration.getProperty("play.netty.bufferChunkedRequests","true"));
        Boolean requestDecompression = Boolean.parseBoolean(Play.configuration.getProperty("play.netty.requestDecompression","false"));
        Boolean responseCompression = Boolean.parseBoolean(Play.configuration.getProperty("play.netty.responseCompression","false"));

        ChannelPipeline pipeline = pipeline();

        // Add SSL handler first to encrypt and decrypt everything.
        SSLEngine engine = SslHttpServerContextFactory.getServerContext().createSSLEngine();
        engine.setUseClientMode(false);
        
        if ("want".equalsIgnoreCase(mode)) {
            engine.setWantClientAuth(true);
        } else if ("need".equalsIgnoreCase(mode)) {
            engine.setNeedClientAuth(true);
        }
        
        engine.setEnableSessionCreation(true);

        pipeline.addLast("flashPolicy", new FlashPolicyHandler());
        pipeline.addLast("ssl", new SslHandler(engine));
        if(requestDecompression) pipeline.addLast("inflater", new HttpContentDecompressor());
        pipeline.addLast("decoder", new HttpRequestDecoder());
        pipeline.addLast("aggregator", bufferedAggregator ? new StreamChunkAggregator(max) : new StreamingChunkAggregator());
        pipeline.addLast("encoder", new HttpResponseEncoder());
        if(responseCompression) pipeline.addLast("deflater", new HttpContentCompressor());
        pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());

        pipeline.addLast("handler", new SslPlayHandler());

        return pipeline;
    }
}

