package io.getlime.banking.security.provider;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

import io.getlime.banking.security.model.ApiAuthentication;
import io.getlime.banking.security.model.PowerAuthAuthentication;
import io.getlime.banking.soap.client.PowerAuthServiceClient;
import io.getlime.banking.util.PowerAuthUtil;
import io.getlime.powerauth.soap.VerifySignatureRequest;
import io.getlime.powerauth.soap.VerifySignatureResponse;

@Component
public class PowerAuthAuthenticationProvider implements AuthenticationProvider {

	@Autowired
	private PowerAuthServiceClient powerAuthClient;

	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {

		PowerAuthAuthentication powerAuthAuthentication = (PowerAuthAuthentication) authentication;

		VerifySignatureRequest soapRequest = new VerifySignatureRequest();
		soapRequest.setActivationId(powerAuthAuthentication.getActivationId());
		soapRequest.setSignature(powerAuthAuthentication.getSignature());
		soapRequest.setSignatureType(powerAuthAuthentication.getSignatureType());
		try {
			String payload = PowerAuthUtil.getSignatureBaseString(powerAuthAuthentication.getHttpMethod(),
					powerAuthAuthentication.getRequestUri(), powerAuthAuthentication.getApplicationSecret(),
					powerAuthAuthentication.getNonce(), powerAuthAuthentication.getData());
			soapRequest.setData(payload);
		} catch (UnsupportedEncodingException | NoSuchAlgorithmException e) {
			return null;
		}

		VerifySignatureResponse soapResponse = powerAuthClient.verifySignature(soapRequest);

		if (soapResponse.isSignatureValid()) {
			ApiAuthentication apiAuthentication = new ApiAuthentication();
			apiAuthentication.setActivationId(soapResponse.getActivationId());
			apiAuthentication.setUserId(soapResponse.getUserId());
			apiAuthentication.setAuthenticated(true);
			return apiAuthentication;
		} else {
			return null;
		}
	}

	@Override
	public boolean supports(Class<?> authentication) {
		if (authentication == PowerAuthAuthentication.class) {
			return true;
		}
		return false;
	}
	
	public ApiAuthentication checkRequestSignature(
			HttpServletRequest servletRequest,
			String requestUriIdentifier,
			String httpAuthorizationHeader) throws Exception {

		if (httpAuthorizationHeader == null || httpAuthorizationHeader.equals("undefined")) {
			throw new Exception("POWER_AUTH_SIGNATURE_INVALID_EMPTY");
		}

		byte[] requestBodyBytes = ((String)servletRequest.getAttribute("X-PowerAuth-Request-Body")).getBytes();

		Map<String, String> httpHeaderInfo = PowerAuthUtil.parsePowerAuthSignatureHTTPHeader(httpAuthorizationHeader);
		
		PowerAuthAuthentication powerAuthAuthentication = new PowerAuthAuthentication();
		powerAuthAuthentication.setActivationId(httpHeaderInfo.get("pa_activation_id"));
		powerAuthAuthentication.setApplicationSecret(httpHeaderInfo.get("pa_application_id")); // here should be the lookup!!!!
		powerAuthAuthentication.setNonce(httpHeaderInfo.get("nonce"));
		powerAuthAuthentication.setSignatureType(httpHeaderInfo.get("signature_type"));
		powerAuthAuthentication.setSignature(httpHeaderInfo.get("pa_signature"));
		powerAuthAuthentication.setHttpMethod(servletRequest.getMethod().toUpperCase());
		powerAuthAuthentication.setRequestUri(requestUriIdentifier);
		powerAuthAuthentication.setData(requestBodyBytes);

		ApiAuthentication auth = (ApiAuthentication) this.authenticate(powerAuthAuthentication);
		
		if (auth == null) {
			throw new Exception("POWER_AUTH_SIGNATURE_INVALID_VALUE");
		}

		return auth;
	}

}
