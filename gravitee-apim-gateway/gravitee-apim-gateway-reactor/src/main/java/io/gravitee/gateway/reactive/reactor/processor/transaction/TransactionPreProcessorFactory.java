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
package io.gravitee.gateway.reactive.reactor.processor.transaction;

import static io.gravitee.gateway.reactive.reactor.processor.transaction.TransactionHeader.DEFAULT_REQUEST_ID_HEADER;
import static io.gravitee.gateway.reactive.reactor.processor.transaction.TransactionHeader.DEFAULT_TRANSACTION_ID_HEADER;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TransactionPreProcessorFactory {

    private final String transactionHeader;
    private final String requestHeader;

    public TransactionPreProcessorFactory(final String transactionHeader, final String requestHeader) {
        this.transactionHeader = transactionHeader == null ? DEFAULT_TRANSACTION_ID_HEADER : transactionHeader;
        this.requestHeader = requestHeader == null ? DEFAULT_REQUEST_ID_HEADER : requestHeader;
    }

    public TransactionPreProcessor create() {
        return new TransactionPreProcessor(transactionHeader, requestHeader);
    }
}
