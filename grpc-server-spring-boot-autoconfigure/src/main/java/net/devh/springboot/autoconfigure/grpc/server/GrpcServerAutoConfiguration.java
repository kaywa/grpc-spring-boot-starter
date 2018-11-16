/*
 * Copyright (c) 2016-2018 Michael Zhang <yidongnan@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
 * Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.devh.springboot.autoconfigure.grpc.server;

import java.util.Collections;
import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import brave.Tracing;
import brave.grpc.GrpcTracing;
import io.grpc.CompressorRegistry;
import io.grpc.DecompressorRegistry;
import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.services.HealthStatusManager;
import io.netty.channel.Channel;
import net.devh.springboot.autoconfigure.grpc.server.security.GrpcSecurityAutoConfiguration;

/**
 * The auto configuration used by Spring-Boot that contains all beans to run a grpc server/service.
 *
 * @author Michael (yidongnan@gmail.com)
 * @since 5/17/16
 */
@Configuration
@EnableConfigurationProperties
@ConditionalOnClass(Server.class)
@Import(GrpcSecurityAutoConfiguration.class)
public class GrpcServerAutoConfiguration {

    @ConditionalOnMissingBean
    @Bean
    public GrpcServerProperties defaultGrpcServerProperties() {
        return new GrpcServerProperties();
    }

    @Bean
    public GlobalServerInterceptorRegistry globalServerInterceptorRegistry() {
        return new GlobalServerInterceptorRegistry();
    }

    @Bean
    public AnnotationGlobalServerInterceptorConfigurer annotationGlobalServerInterceptorConfigurer() {
        return new AnnotationGlobalServerInterceptorConfigurer();
    }

    @ConditionalOnMissingBean
    @Bean
    public GrpcServiceDiscoverer defaultGrpcServiceDiscoverer() {
        return new AnnotationGrpcServiceDiscoverer();
    }

    @ConditionalOnMissingBean
    @Bean
    public HealthStatusManager healthStatusManager() {
        return new HealthStatusManager();
    }

    @ConditionalOnBean(CompressorRegistry.class)
    @Bean
    public GrpcServerConfigurer compressionServerConfigurer(final CompressorRegistry registry) {
        return builder -> builder.compressorRegistry(registry);
    }

    @ConditionalOnBean(DecompressorRegistry.class)
    @Bean
    public GrpcServerConfigurer decompressionServerConfigurer(final DecompressorRegistry registry) {
        return builder -> builder.decompressorRegistry(registry);
    }

    @ConditionalOnMissingBean(GrpcServerConfigurer.class)
    @Bean
    public List<GrpcServerConfigurer> defaultServerConfigurers() {
        return Collections.emptyList();
    }

    @ConditionalOnMissingBean
    @ConditionalOnClass({Channel.class, NettyServerBuilder.class})
    @Bean
    public GrpcServerFactory nettyGrpcServerFactory(final GrpcServerProperties properties,
            final GrpcServiceDiscoverer serviceDiscoverer, final List<GrpcServerConfigurer> serverConfigurers) {
        final NettyGrpcServerFactory factory = new NettyGrpcServerFactory(properties, serverConfigurers);
        for (final GrpcServiceDefinition service : serviceDiscoverer.findGrpcServices()) {
            factory.addService(service);
        }
        return factory;
    }

    @ConditionalOnMissingBean
    @Bean
    public GrpcServerLifecycle grpcServerLifecycle(final GrpcServerFactory factory) {
        return new GrpcServerLifecycle(factory);
    }

    @Configuration
    @ConditionalOnProperty(value = "spring.sleuth.scheduled.enabled", matchIfMissing = true)
    @AutoConfigureAfter(TraceAutoConfiguration.class)
    @ConditionalOnBean(Tracing.class)
    @ConditionalOnClass(GrpcTracing.class)
    protected static class TraceServerAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public GrpcTracing grpcTracing(final Tracing tracing) {
            return GrpcTracing.create(tracing);
        }

        @Bean
        public GlobalServerInterceptorConfigurer globalTraceServerInterceptorConfigurerAdapter(
                final GrpcTracing grpcTracing) {
            return registry -> registry.addServerInterceptors(grpcTracing.newServerInterceptor());
        }

    }

}
