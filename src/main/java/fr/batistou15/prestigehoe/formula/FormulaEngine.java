package fr.batistou15.prestigehoe.formula;

import fr.batistou15.prestigehoe.config.ConfigManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

/**
 * Petit moteur de formules pour lire et évaluer
 * les expressions définies dans config.yml > formulas.*.
 *
 * Supporte :
 *  - +, -, *, /, ^
 *  - Parenthèses ( )
 *  - Variables : noms alphanumériques/underscore (ex: level, base_max_level)
 *
 * Limites :
 *  - Pas de fonctions (sin, cos, etc.)
 *  - Pas (ou très peu) de gestion de "unary -" (expressions du type "-5" à éviter)
 */
public class FormulaEngine {

    private final ConfigManager configManager;

    public FormulaEngine(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Évalue une formule définie à path (ex: "formulas.xp_required_per_level").
     *
     * @param path         Chemin dans config.yml (sans ".expression").
     * @param vars         Variables passées par le code (ex: level, base_price, prestige…).
     * @param defaultValue Valeur de fallback si problème.
     * @return résultat de l’évaluation, ou defaultValue si erreur.
     */
    public double eval(String path, Map<String, Double> vars, double defaultValue) {
        try {
            FileConfiguration cfg = configManager.getMainConfig();

            String expr = cfg.getString(path + ".expression", null);
            if (expr == null || expr.trim().isEmpty()) {
                return defaultValue;
            }

            // Variables définies dans config.yml
            ConfigurationSection varSec = cfg.getConfigurationSection(path + ".variables");
            Map<String, Double> fullVars = new HashMap<>();

            if (varSec != null) {
                for (String key : varSec.getKeys(false)) {
                    fullVars.put(key, varSec.getDouble(key));
                }
            }

            // Vars passées par le code prioritaire
            if (vars != null) {
                for (Map.Entry<String, Double> e : vars.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null) {
                        fullVars.put(e.getKey(), e.getValue());
                    }
                }
            }

            return evaluateExpression(expr, fullVars);
        } catch (Exception ex) {
            ex.printStackTrace();
            return defaultValue;
        }
    }

    // ==========================
    //   PARSE & EVAL EXPRESSION
    // ==========================

    private enum TokenType {
        NUMBER,
        VARIABLE,
        OPERATOR,
        PAREN_LEFT,
        PAREN_RIGHT
    }

    private static class Token {
        TokenType type;
        String text;

        Token(TokenType type, String text) {
            this.type = type;
            this.text = text;
        }
    }

    private double evaluateExpression(String expr, Map<String, Double> vars) {
        List<Token> tokens = tokenize(expr);
        List<Token> rpn = toRPN(tokens);
        return evalRPN(rpn, vars);
    }

    private List<Token> tokenize(String expr) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int len = expr.length();

        while (i < len) {
            char c = expr.charAt(i);

            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            // Nombre (0-9 ou .)
            if (Character.isDigit(c) || c == '.') {
                int start = i;
                i++;
                while (i < len) {
                    char c2 = expr.charAt(i);
                    if (Character.isDigit(c2) || c2 == '.') {
                        i++;
                    } else {
                        break;
                    }
                }
                tokens.add(new Token(TokenType.NUMBER, expr.substring(start, i)));
                continue;
            }

            // Variable (lettre ou underscore)
            if (Character.isLetter(c) || c == '_') {
                int start = i;
                i++;
                while (i < len) {
                    char c2 = expr.charAt(i);
                    if (Character.isLetterOrDigit(c2) || c2 == '_') {
                        i++;
                    } else {
                        break;
                    }
                }
                tokens.add(new Token(TokenType.VARIABLE, expr.substring(start, i)));
                continue;
            }

            // Parenthèses
            if (c == '(') {
                tokens.add(new Token(TokenType.PAREN_LEFT, "("));
                i++;
                continue;
            }
            if (c == ')') {
                tokens.add(new Token(TokenType.PAREN_RIGHT, ")"));
                i++;
                continue;
            }

            // Opérateurs
            if ("+-*/^".indexOf(c) >= 0) {
                tokens.add(new Token(TokenType.OPERATOR, String.valueOf(c)));
                i++;
                continue;
            }

            // caractère inconnu -> on skip
            i++;
        }

        return tokens;
    }

    private int precedence(String op) {
        return switch (op) {
            case "+", "-" -> 1;
            case "*", "/" -> 2;
            case "^" -> 3;
            default -> 0;
        };
    }

    private boolean isRightAssociative(String op) {
        return "^".equals(op);
    }

    private List<Token> toRPN(List<Token> tokens) {
        List<Token> output = new ArrayList<>();
        Deque<Token> stack = new ArrayDeque<>();

        for (Token t : tokens) {
            switch (t.type) {
                case NUMBER, VARIABLE -> output.add(t);

                case OPERATOR -> {
                    String o1 = t.text;
                    while (!stack.isEmpty()) {
                        Token top = stack.peek();
                        if (top.type != TokenType.OPERATOR) break;
                        String o2 = top.text;

                        int p1 = precedence(o1);
                        int p2 = precedence(o2);

                        if ((!isRightAssociative(o1) && p1 <= p2) || (isRightAssociative(o1) && p1 < p2)) {
                            output.add(stack.pop());
                        } else {
                            break;
                        }
                    }
                    stack.push(t);
                }

                case PAREN_LEFT -> stack.push(t);

                case PAREN_RIGHT -> {
                    while (!stack.isEmpty() && stack.peek().type != TokenType.PAREN_LEFT) {
                        output.add(stack.pop());
                    }
                    if (!stack.isEmpty() && stack.peek().type == TokenType.PAREN_LEFT) {
                        stack.pop();
                    }
                }
            }
        }

        while (!stack.isEmpty()) {
            Token t = stack.pop();
            if (t.type == TokenType.PAREN_LEFT || t.type == TokenType.PAREN_RIGHT) {
                continue;
            }
            output.add(t);
        }

        return output;
    }

    private double evalRPN(List<Token> rpn, Map<String, Double> vars) {
        Deque<Double> stack = new ArrayDeque<>();

        for (Token t : rpn) {
            switch (t.type) {
                case NUMBER -> {
                    double val = Double.parseDouble(t.text);
                    stack.push(val);
                }
                case VARIABLE -> {
                    double val = vars.getOrDefault(t.text, 0.0);
                    stack.push(val);
                }
                case OPERATOR -> {
                    if (stack.size() < 2) {
                        throw new IllegalArgumentException("Expression invalide: manque des opérandes pour " + t.text);
                    }
                    double b = stack.pop();
                    double a = stack.pop();
                    double res;
                    switch (t.text) {
                        case "+" -> res = a + b;
                        case "-" -> res = a - b;
                        case "*" -> res = a * b;
                        case "/" -> res = b == 0 ? 0 : a / b;
                        case "^" -> res = Math.pow(a, b);
                        default -> throw new IllegalArgumentException("Opérateur inconnu: " + t.text);
                    }
                    stack.push(res);
                }
                default -> {
                    // rien
                }
            }
        }

        if (stack.isEmpty()) {
            return 0.0;
        }
        return stack.pop();
    }
}
