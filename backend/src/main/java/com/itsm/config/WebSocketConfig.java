package com.itsm.config;

import com.itsm.repository.UserRepository;
import com.itsm.security.JwtService;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;

/**
 * STOMP over WebSocket for real-time notifications and live dashboards.
 * The client sends its JWT in the STOMP CONNECT frame ({@code Authorization: Bearer ...}); the interceptor
 * validates it and binds the session to the user id, so {@code /user/queue/notifications} is private.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ItsmProperties properties;

    public WebSocketConfig(JwtService jwtService, UserRepository userRepository, ItsmProperties properties) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.properties = properties;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(properties.corsAllowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String header = accessor.getFirstNativeHeader("Authorization");
                    String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
                    Long userId = token == null ? null : jwtService.validate(token)
                            .filter(id -> userRepository.findById(id).map(u -> u.isActive()).orElse(false))
                            .orElse(null);
                    if (userId == null) {
                        throw new MessagingException("Invalid or missing JWT on WebSocket CONNECT");
                    }
                    String name = String.valueOf(userId);
                    Principal principal = () -> name;
                    accessor.setUser(principal);
                }
                return message;
            }
        });
    }
}
