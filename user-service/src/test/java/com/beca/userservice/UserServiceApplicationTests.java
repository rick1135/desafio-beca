package com.beca.userservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "INTERNAL_AUTH_KEY=chave-de-teste")
class UserServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
