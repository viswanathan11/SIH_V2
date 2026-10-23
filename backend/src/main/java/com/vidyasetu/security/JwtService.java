package com.vidyasetu.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class JwtService {
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String DEFAULT_KID = "vidyasetu-key-1";

    private final ECKey ecJwk;
    private final ECDSASigner signer;
    private final ECDSAVerifier verifier;
    private final int accessTtlMinutes;

    public JwtService(@Value("${app.jwt.signing-key-path:}") String keyPath,
                      @Value("${app.jwt.access-ttl-minutes:60}") int accessTtlMinutes,
                      @Value("${app.data-dir:./data}") String dataDir) {
        this.accessTtlMinutes = accessTtlMinutes;
        ECKey key = null;
        try {
            File file = (keyPath != null && !keyPath.isBlank()) ? new File(keyPath) : new File(dataDir, "jwt_es256.json");
            if (file.exists()) {
                String json = Files.readString(file.toPath());
                key = ECKey.parse(json);
                log.info("Loaded ES256 key from {}", file.getAbsolutePath());
            } else {
                key = new ECKeyGenerator(Curve.P_256).keyID(DEFAULT_KID).generate();
                file.getParentFile().mkdirs();
                Files.writeString(file.toPath(), key.toJSONString());
                log.info("Generated new ES256 key and saved to {}", file.getAbsolutePath());
            }
        } catch (Exception e) {
            log.warn("Could not load/save ES256 key from file, creating ephemeral in-memory key: {}", e.getMessage());
            try {
                key = new ECKeyGenerator(Curve.P_256).keyID(DEFAULT_KID).generate();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
        this.ecJwk = key;
        try {
            this.signer = new ECDSASigner(this.ecJwk);
            this.verifier = new ECDSAVerifier(this.ecJwk.toECPublicKey());
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize ES256 signer/verifier", e);
        }
    }

    public String issueAccessToken(UserPrincipal user) {
        Instant now = Instant.now();
        Instant exp = now.plus(accessTtlMinutes, ChronoUnit.MINUTES);

        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer("vidyasetu")
                .subject(user.getId().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp))
                .claim("email", user.getEmail())
                .claim("fullName", user.getFullName())
                .claim("roles", user.getRoles())
                .claim("permissions", user.getPermissions());

        if (user.getInstitutionId() != null) builder.claim("institutionId", user.getInstitutionId().toString());
        if (user.getInstitutionCode() != null) builder.claim("institutionCode", user.getInstitutionCode());
        if (user.getInstitutionName() != null) builder.claim("institutionName", user.getInstitutionName());
        if (user.getStudentId() != null) builder.claim("studentId", user.getStudentId().toString());
        if (user.getApaarMasked() != null) builder.claim("apaarMasked", user.getApaarMasked());
        if (user.getOrg() != null) builder.claim("org", user.getOrg());

        try {
            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(ecJwk.getKeyID()).type(JOSEObjectType.JWT).build(),
                    builder.build()
            );
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign JWT", e);
        }
    }

    public JWTClaimsSet verifyAccessToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            if (!signedJWT.verify(verifier)) {
                return null;
            }
            Date exp = signedJWT.getJWTClaimsSet().getExpirationTime();
            if (exp != null && exp.before(new Date())) {
                return null;
            }
            return signedJWT.getJWTClaimsSet();
        } catch (Exception e) {
            return null;
        }
    }

    /** Issue compact JWS verifiable student record */
    public String issueVerifiableRecord(String jti, String studentSubHmac, Map<String, Object> claims, Instant expiresAt) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer("vidyasetu-record-authority")
                .jwtID(jti)
                .subject(studentSubHmac)
                .issueTime(Date.from(now));

        if (expiresAt != null) {
            builder.expirationTime(Date.from(expiresAt));
        }
        claims.forEach(builder::claim);

        try {
            SignedJWT signed = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(ecJwk.getKeyID()).type(new JOSEObjectType("VSR")).build(),
                    builder.build()
            );
            signed.sign(signer);
            return signed.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign verifiable record JWS", e);
        }
    }

    public JWTClaimsSet verifyVerifiableRecord(String jws) {
        try {
            SignedJWT signed = SignedJWT.parse(jws);
            if (!signed.verify(verifier)) {
                return null;
            }
            return signed.getJWTClaimsSet();
        } catch (Exception e) {
            return null;
        }
    }

    public Map<String, Object> getJwksMap() {
        JWKSet jwkSet = new JWKSet(ecJwk.toPublicJWK());
        return jwkSet.toJSONObject();
    }

    public ECKey getPublicJwk() {
        return ecJwk.toPublicJWK();
    }
}
