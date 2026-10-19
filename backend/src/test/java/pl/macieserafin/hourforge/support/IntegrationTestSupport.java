package pl.macieserafin.hourforge.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestData.class)
public abstract class IntegrationTestSupport {
    @Autowired protected TestData fixtures;
}
