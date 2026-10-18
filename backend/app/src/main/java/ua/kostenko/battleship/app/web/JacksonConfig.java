package ua.kostenko.battleship.app.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Routes the generated Jackson 2 wire DTOs through a strict Jackson 2 mapper. Spring Boot's own converter is Jackson 3
 * and the generated mapper tolerates unknown properties, so neither may bind these types (R37). The one mapper is a
 * bean shared by the HTTP converter, the problem writer and the event stream, so every wire byte has one
 * serialization path (R17).
 */
@Configuration(proxyBeanMethods = false)
class JacksonConfig implements WebMvcConfigurer {
    private static final String WIRE_PACKAGE = "ua.kostenko.battleship.app.web.dto";

    private final ObjectMapper wire;

    JacksonConfig(ObjectMapper wire) {
        this.wire = wire;
    }

    @Bean
    static ObjectMapper wireObjectMapper() {
        return wireMapper();
    }

    static ObjectMapper wireMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .addModule(new SimpleModule().addSerializer(OffsetDateTime.class, new MillisUtcSerializer()))
                .defaultPropertyInclusion(
                        JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .build();
    }

    /**
     * Every timestamp the contract shows is UTC with exactly three fractional digits ({@code 2026-09-20T12:00:00.000Z}),
     * whereas ISO formatting trims trailing zeros ({@code ...:00Z}, {@code ...:00.1Z}), so one fixed form is written.
     */
    private static final class MillisUtcSerializer extends StdSerializer<OffsetDateTime> {
        private static final DateTimeFormatter MILLIS_UTC = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'");

        MillisUtcSerializer() {
            super(OffsetDateTime.class);
        }

        @Override
        public void serialize(OffsetDateTime value, JsonGenerator generator, SerializerProvider provider)
                throws IOException {
            generator.writeString(MILLIS_UTC.format(value.withOffsetSameInstant(ZoneOffset.UTC)));
        }
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.addFirst(new WireDtoConverter(wire));
    }

    /** Handles only the generated wire types, so every other body keeps Spring Boot's default converter. */
    @SuppressWarnings("removal")
    private static final class WireDtoConverter extends AbstractJackson2HttpMessageConverter {
        WireDtoConverter(ObjectMapper mapper) {
            super(mapper, MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON);
        }

        @Override
        public boolean canRead(Type type, Class<?> contextClass, MediaType mediaType) {
            return type instanceof Class<?> clazz && isWireType(clazz) && super.canRead(type, contextClass, mediaType);
        }

        @Override
        public boolean canWrite(Class<?> clazz, MediaType mediaType) {
            return isWireType(clazz) && super.canWrite(clazz, mediaType);
        }

        private static boolean isWireType(Class<?> clazz) {
            return clazz.getPackageName().equals(WIRE_PACKAGE);
        }
    }
}
