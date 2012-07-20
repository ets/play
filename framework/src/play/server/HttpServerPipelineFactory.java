package play.server;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;

import play.Play;

import static org.jboss.netty.channel.Channels.pipeline;

public class HttpServerPipelineFactory implements ChannelPipelineFactory {

    public ChannelPipeline getPipeline() throws Exception {

        Integer max = Integer.valueOf(Play.configuration.getProperty("play.netty.maxContentLength", "-1"));
        Boolean bufferedAggregator = Boolean.parseBoolean(Play.configuration.getProperty("play.netty.bufferChunkedRequests","true"));
           
        ChannelPipeline pipeline = pipeline();
        PlayHandler playHandler = new PlayHandler();
        
        pipeline.addLast("flashPolicy", new FlashPolicyHandler()); 
        pipeline.addLast("decoder", new HttpRequestDecoder());        
        pipeline.addLast("aggregator", bufferedAggregator ? new StreamChunkAggregator(max) : new StreamingChunkAggregator());
        
        pipeline.addLast("encoder", new HttpResponseEncoder());
        pipeline.addLast("chunkedWriter", playHandler.chunkedWriteHandler);
        pipeline.addLast("handler", playHandler);

        return pipeline;
    }
}

