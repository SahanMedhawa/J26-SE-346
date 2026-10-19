package akegp.enforcement.api;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * The build does not use the compiler flag -parameters, so Spring cannot guess a parameter's name. Every
 * @PathVariable and @RequestHeader in this module must therefore carry its name, or the endpoint fails at runtime
 * with "Name for argument ... not specified" (HTTP 500).
 */
class ParameterNamesTest {
  private static final Class<?>[] CONTROLLERS = {PipelineEnforcementApi.class};

  @Test void everyPathVariableAndHeaderHasAnExplicitName() {
    for (Class<?> controller : CONTROLLERS) {
      for (Method method : controller.getDeclaredMethods()) {
        for (Parameter parameter : method.getParameters()) {
          PathVariable path = parameter.getAnnotation(PathVariable.class);
          if (path != null) assertTrue(!path.value().isBlank() || !path.name().isBlank(),
              controller.getSimpleName() + "." + method.getName() + ": @PathVariable needs a name");
          RequestHeader header = parameter.getAnnotation(RequestHeader.class);
          if (header != null) assertTrue(!header.value().isBlank() || !header.name().isBlank(),
              controller.getSimpleName() + "." + method.getName() + ": @RequestHeader needs a name");
        }
      }
    }
  }
}
