/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.rest.api.service.validator;

import io.gravitee.rest.api.service.PasswordValidator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RegexPasswordValidator implements PasswordValidator, InitializingBean {

    private Pattern pattern;
    private Matcher matcher;

    @Value(
        "${user.password.policy.pattern:^(?:(?=.*\\d)(?=.*[A-Z])(?=.*[a-z])|(?=.*\\d)(?=.*[^A-Za-z0-9])(?=.*[a-z])|(?=.*[^A-Za-z0-9])(?=.*[A-Z])(?=.*[a-z])|(?=.*\\d)(?=.*[A-Z])(?=.*[^A-Za-z0-9]))(?!.*(.)\\1{2,})[A-Za-z0-9!~<>,;:_\\-=?*+#.\"'&§`£€%°()\\\\\\|\\[\\]\\-\\$\\^\\@\\/]{8,32}$}"
    )
    private String passwordPattern;

    public RegexPasswordValidator() {}

    public RegexPasswordValidator(String pattern) {
        this.pattern = Pattern.compile(pattern);
    }

    @Override
    public boolean validate(final String password) {
        matcher = pattern.matcher(password);
        return matcher.matches();
    }

    @Override
    public void afterPropertiesSet() {
        pattern = Pattern.compile(passwordPattern);
    }
}
