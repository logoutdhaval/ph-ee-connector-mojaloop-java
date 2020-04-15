package org.mifos.connector.mojaloop.payer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.client.ZeebeClient;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.mifos.connector.mojaloop.ilp.IlpBuilder;
import org.mifos.phee.common.mojaloop.dto.QuoteSwitchResponseDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.mifos.connector.mojaloop.camel.config.CamelProperties.CACHED_TRANSACTION_ID;
import static org.mifos.connector.mojaloop.camel.config.CamelProperties.ERROR_INFORMATION;
import static org.mifos.connector.mojaloop.camel.config.CamelProperties.PAYEE_QUOTE_FAILED;
import static org.mifos.connector.mojaloop.camel.config.CamelProperties.PAYEE_QUOTE_RESPONSE;

@Component
public class QuoteResponseProcessor implements Processor {

    @Autowired
    private ZeebeClient zeebeClient;

    @Autowired
    private IlpBuilder ilpBuilder;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void process(Exchange exchange) throws JsonProcessingException {
        Map<String, Object> variables = new HashMap<>();
        if (exchange.getProperty(PAYEE_QUOTE_FAILED, Boolean.class)) {
            variables.put(ERROR_INFORMATION, exchange.getIn().getBody(String.class));
            variables.put(PAYEE_QUOTE_FAILED, true);
        } else {
            QuoteSwitchResponseDTO response = exchange.getIn().getBody(QuoteSwitchResponseDTO.class);
            if (!ilpBuilder.isValidPacketAgainstCondition(response.getIlpPacket(), response.getCondition())) {
                throw new RuntimeException("Invalid ILP packet!");
            }
            variables.put(PAYEE_QUOTE_RESPONSE, objectMapper.writeValueAsString(response));
        }

        zeebeClient.newPublishMessageCommand()
                .messageName("quote")
                .correlationKey(exchange.getProperty(CACHED_TRANSACTION_ID, String.class))
                .timeToLive(Duration.ofMillis(30000))
                .variables(variables)
                .send();
    }
}
