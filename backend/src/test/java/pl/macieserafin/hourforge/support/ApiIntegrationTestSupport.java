package pl.macieserafin.hourforge.support;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
public abstract class ApiIntegrationTestSupport extends IntegrationTestSupport {
}
