package com.srmasset.creditengine.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Base HTTP: mesmo PostgreSQL real da integração, com MockMvc para asserções de contrato. */
@AutoConfigureMockMvc
public abstract class WebIntegrationTestBase extends IntegrationTestBase {

    @Autowired
    protected MockMvc mvc;
}
