package wolf.arch;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(value = { ElementType.TYPE, ElementType.PARAMETER })
@Retention(value = RetentionPolicy.RUNTIME)
public @interface DisplayName {

	String value();

	String plural() default "";

}
