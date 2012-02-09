    package play.server;

import static org.jboss.netty.channel.Channels.pipeline;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.handler.codec.http.FixedLengthPassthruHttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;

import play.Play;

public class HttpServerPipelineFactory implements ChannelPipelineFactory {

    public ChannelPipeline getPipeline() throws Exception {

        Integer max = Integer.valueOf(Play.configuration.getProperty("play.netty.maxContentLength", "-1"));
        Boolean passThruFixedLengthRequests = Boolean.valueOf(Play.configuration.getProperty("play.netty.fixedlengthRequests.passthru"));
           
        ChannelPipeline pipeline = pipeline();
        PlayHandler playHandler = new PlayHandler();
        
        pipeline.addLast("flashPolicy", new FlashPolicyHandler()); 
        pipeline.addLast("decoder", passThruFixedLengthRequests ? new FixedLengthPassthruHttpRequestDecoder() : new HttpRequestDecoder());
        pipeline.addLast("aggregator", new StreamChunkAggregator(max));        
        pipeline.addLast("encoder", new HttpResponseEncoder());
        pipeline.addLast("chunkedWriter", playHandler.chunkedWriteHandler);
        pipeline.addLast("handler", playHandler);

        return pipeline;
    }
}

