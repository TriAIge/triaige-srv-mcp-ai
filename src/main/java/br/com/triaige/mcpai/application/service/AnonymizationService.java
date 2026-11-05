package br.com.triaige.mcpai.application.service;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * T2 — anonimização por regex, migração/adaptação de ex-anonimizador.py
 * como módulo interno (não é um serviço externo). CPF/CNPJ são validados por dígito
 * verificador (mod 11) para reduzir falsos positivos — as demais categorias não têm
 * checksum disponível e dependem de padrão estrutural/contextual (limitação conhecida,
 * risco aceito).
 *
 * <p>Limitações documentadas (deliberadas, para conter falsos positivos):
 * <ul>
 *   <li>CPF/telefone totalmente sem qualquer separador (11 dígitos corridos) não é
 *       distinguível de forma confiável entre as duas categorias por regex puro; o
 *       reconhecimento de telefone exige o hífen entre os dois últimos grupos.</li>
 *   <li>CEP só é reconhecido no formato com hífen (12345-678) — 8 dígitos corridos sem
 *       separador colidem estruturalmente com telefone fixo sem DDD.</li>
 *   <li>RG só é reconhecido quando precedido por um termo de contexto (RG, R.G.,
 *       "registro geral", "identidade"), já que o formato varia por estado e não tem
 *       dígito verificador público.</li>
 *   <li>Nome completo é a categoria mais fraca por natureza: heurística
 *       de duas ou mais palavras consecutivas capitalizadas, com uma lista mínima de
 *       termos jurídicos/institucionais para reduzir os falsos positivos mais óbvios.</li>
 * </ul>
 */
@Service
public class AnonymizationService {

    private static final Pattern EMAIL = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");

    private static final Pattern CNPJ = Pattern.compile(
            "\\b\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}\\b");

    private static final Pattern CPF = Pattern.compile(
            "\\b\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}\\b");

    private static final Pattern CEP = Pattern.compile(
            "\\b\\d{5}-\\d{3}\\b");

    private static final Pattern TELEFONE = Pattern.compile(
            "\\b(?:\\+55\\s?)?(?:\\(\\d{2}\\)\\s?|\\d{2}\\s)?9?\\d{4}-\\d{4}\\b");

    private static final Pattern RG_CONTEXT = Pattern.compile(
            "(?i)\\b(?:rg|r\\.g\\.|registro\\s+geral|identidade)\\b\\s*[:.\\-]?\\s*"
                    + "(\\d{1,2}\\.?\\d{3}\\.?\\d{3}-?[0-9Xx])");

    private static final Pattern ENDERECO = Pattern.compile(
            "(?i)\\b(?:rua|av\\.?|avenida|alameda|al\\.|travessa|trav\\.|rodovia|rod\\.|estrada|"
                    + "pra[cç]a|p[cç]a\\.?)\\s+[A-Za-zÀ-ÿ0-9.'-]+(?:\\s+[A-Za-zÀ-ÿ0-9.'-]+){0,5},?\\s*"
                    + "(?:n[ºo°.]?\\s*)?\\d{1,6}\\b");

    private static final Pattern NOME_COMPLETO = Pattern.compile(
            "\\b(?:[A-ZÀ-Ý][a-zà-ÿ]+\\s+){1,4}[A-ZÀ-Ý][a-zà-ÿ]+\\b");

    private static final Set<String> NOME_STOPWORDS = Set.of(
            "poder judiciario", "vara civel", "vara criminal", "vara de familia",
            "ministerio publico", "tribunal de justica", "justica federal", "justica estadual",
            "codigo civil", "codigo penal", "codigo de processo civil", "codigo de defesa do consumidor",
            "supremo tribunal", "superior tribunal", "tribunal federal", "distrito federal",
            "boa fe", "boa-fe", "termo de", "anexo a", "anexo b");

    public AnonymizationResult anonymize(String text) {
        if (text == null || text.isBlank()) {
            return new AnonymizationResult(text, Map.of());
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        String result = text;

        result = redact(result, EMAIL, "EMAIL", counts, mr -> true);
        result = redact(result, CNPJ, "CNPJ", counts, mr -> isValidCnpj(digitsOnly(mr.group())));
        result = redact(result, CPF, "CPF", counts, mr -> isValidCpf(digitsOnly(mr.group())));
        result = redact(result, CEP, "CEP", counts, mr -> true);
        result = redact(result, TELEFONE, "TELEFONE", counts, mr -> true);
        result = redactRg(result, counts);
        result = redact(result, ENDERECO, "ENDERECO", counts, mr -> true);
        result = redactNomes(result, counts);

        return new AnonymizationResult(result, counts);
    }

    private String redact(String text, Pattern pattern, String label, Map<String, Integer> counts,
                           Predicate<MatchResult> validator) {
        Matcher matcher = pattern.matcher(text);
        return matcher.replaceAll(mr -> {
            if (validator.test(mr)) {
                counts.merge(label, 1, Integer::sum);
                return "[" + label + "]";
            }
            return mr.group();
        });
    }

    private String redactRg(String text, Map<String, Integer> counts) {
        Matcher matcher = RG_CONTEXT.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            counts.merge("RG", 1, Integer::sum);
            String whole = matcher.group(0);
            String digits = matcher.group(1);
            String prefix = whole.substring(0, whole.length() - digits.length());
            matcher.appendReplacement(sb, Matcher.quoteReplacement(prefix + "[RG]"));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String redactNomes(String text, Map<String, Integer> counts) {
        Matcher matcher = NOME_COMPLETO.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String candidate = matcher.group();
            if (NOME_STOPWORDS.contains(normalize(candidate))) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(candidate));
                continue;
            }
            counts.merge("NOME", 1, Integer::sum);
            matcher.appendReplacement(sb, "[NOME]");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String normalize(String s) {
        String noAccents = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.toLowerCase();
    }

    private String digitsOnly(String s) {
        return s.replaceAll("\\D", "");
    }

    /** Validação de CPF por dígito verificador (mod 11). */
    boolean isValidCpf(String cpf) {
        if (cpf.length() != 11 || allDigitsEqual(cpf)) {
            return false;
        }
        int[] d = toDigits(cpf);

        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += d[i] * (10 - i);
        }
        int dv1 = checkDigit(sum);
        if (dv1 != d[9]) {
            return false;
        }

        sum = 0;
        for (int i = 0; i < 10; i++) {
            sum += d[i] * (11 - i);
        }
        int dv2 = checkDigit(sum);
        return dv2 == d[10];
    }

    /** Validação de CNPJ por dígito verificador (mod 11). */
    boolean isValidCnpj(String cnpj) {
        if (cnpj.length() != 14 || allDigitsEqual(cnpj)) {
            return false;
        }
        int[] d = toDigits(cnpj);
        int[] w1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] w2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

        int sum = 0;
        for (int i = 0; i < 12; i++) {
            sum += d[i] * w1[i];
        }
        int r = sum % 11;
        int dv1 = (r < 2) ? 0 : 11 - r;
        if (dv1 != d[12]) {
            return false;
        }

        sum = 0;
        for (int i = 0; i < 13; i++) {
            sum += d[i] * w2[i];
        }
        r = sum % 11;
        int dv2 = (r < 2) ? 0 : 11 - r;
        return dv2 == d[13];
    }

    private int checkDigit(int sum) {
        int r = 11 - (sum % 11);
        return (r >= 10) ? 0 : r;
    }

    private boolean allDigitsEqual(String digits) {
        return digits.chars().distinct().count() == 1;
    }

    private int[] toDigits(String s) {
        int[] d = new int[s.length()];
        for (int i = 0; i < s.length(); i++) {
            d[i] = s.charAt(i) - '0';
        }
        return d;
    }
}
