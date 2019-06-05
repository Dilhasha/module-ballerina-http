// Copyright (c) 2018 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

import ballerina/auth;
import ballerina/log;

# Representation of Bearer auth header authn handler for both inbound and outbound HTTP traffic.
#
# + authProvider - `InboundAuthProvider` instance or `OutboundAuthProvider` instance
public type BearerAuthHeaderAuthnHandler object {

    *InboundAuthnHandler;
    *OutboundAuthnHandler;

    public auth:InboundAuthProvider|auth:OutboundAuthProvider authProvider;

    public function __init(auth:InboundAuthProvider|auth:OutboundAuthProvider authProvider) {
        self.authProvider = authProvider;
    }

    # Checks if the request can be authenticated with Bearer auth header.
    #
    # + req - `Request` instance
    # + return - `true` if can be authenticated, else `false`
    public function canHandle(Request req) returns boolean {
        if (req.hasHeader(AUTH_HEADER)) {
            string headerValue = extractAuthorizationHeaderValue(req);
            return headerValue.hasPrefix(auth:AUTH_SCHEME_BEARER);
        }
        return false;
    }

    # Authenticates the incoming request with the use of credentials passed as Bearer auth header.
    #
    # + req - `Request` instance
    # + return - `true` if authenticated successfully, else `false`, or `error` in case of errors
    public function handle(Request req) returns boolean|error {
        string headerValue = extractAuthorizationHeaderValue(req);
        string credential = headerValue.substring(6, headerValue.length()).trim();
        var authProvider = self.authProvider;
        if (authProvider is auth:InboundAuthProvider) {
            return authProvider.authenticate(credential);
        } else {
            return prepareError("Outbound auth provider is configured for inbound authentication.");
        }
    }

    # Prepare the request with Bearer auth header.
    #
    # + req - `Request` instance
    # + return - `Request` instance or `error` in case of errors
    public function prepare(Request req) returns error? {
        var authProvider = self.authProvider;
        if (authProvider is auth:OutboundAuthProvider) {
            string token = check authProvider.generateToken();
            req.setHeader(AUTH_HEADER, auth:AUTH_SCHEME_BEARER + token);
        } else {
            return prepareError("Inbound auth provider is configured for outbound authentication.");
        }
    }
};
