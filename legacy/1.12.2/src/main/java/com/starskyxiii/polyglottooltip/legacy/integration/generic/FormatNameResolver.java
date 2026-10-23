package com.starskyxiii.polyglottooltip.legacy.integration.generic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.minecraft.ConsistencyCheck;
import com.starskyxiii.polyglottooltip.legacy.minecraft.ResolvedName;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryDictionary;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SpecialItemNameResolver;

import net.minecraft.item.ItemStack;

public final class FormatNameResolver implements SpecialItemNameResolver {
    private static final Pattern POSITION = Pattern.compile("%(?:([1-9]\\d?)\\$)?s|%%");
    private static final int LITERAL = -2;
    private static final int LITERAL_PERCENT = -1;

    private final IntegrationGuard guard;

    public FormatNameResolver(IntegrationGuard guard) {
        this.guard = guard;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return guard.active();
    }

    @Override
    public String resolve(ItemStack stack, ConsistencyCheck.Result result, String languageCode,
                          SecondaryDictionary dictionary) {
        ResolvedName name = resolveName(stack, result, languageCode, dictionary);
        return name == null ? null : name.name();
    }

    @Override
    public ResolvedName resolveName(ItemStack stack, ConsistencyCheck.Result result, String languageCode,
                                    SecondaryDictionary dictionary) {
        return guard.call(() -> resolve(result, dictionary), null);
    }

    private static ResolvedName resolve(ConsistencyCheck.Result result, SecondaryDictionary dictionary) {
        String format = result.expected();
        if (format == null || !hasPositions(format)) {
            return null;
        }
        String secondaryFormat = dictionary.get(result.key());
        if (secondaryFormat == null) {
            return null;
        }
        ReverseIndex index = ReverseIndex.current();
        return compose(format, result.primary(), secondaryFormat, text -> index.translate(text, dictionary));
    }

    static boolean hasPositions(String format) {
        for (Token token : tokens(format)) {
            if (token.position >= 0) {
                return true;
            }
        }
        return false;
    }

    static ResolvedName compose(String primaryFormat, String actual, String secondaryFormat,
                                Function<String, String> translate) {
        if (primaryFormat == null || actual == null || secondaryFormat == null) {
            return null;
        }
        List<String> arguments = arguments(primaryFormat, actual);
        if (arguments == null) {
            return null;
        }
        List<String> translated = new ArrayList<>(arguments.size());
        List<String> untranslated = new ArrayList<>();
        for (String argument : arguments) {
            String value = argument == null ? null : translate.apply(argument.trim());
            if (value == null && argument != null && CompositeNameResolver.hasLetter(argument)) {
                untranslated.add(argument.trim());
            }
            translated.add(value != null ? value : argument);
        }
        String composed = render(secondaryFormat, translated);
        if (composed == null || ReverseIndex.normalize(composed).equals(ReverseIndex.normalize(actual))) {
            return null;
        }
        return ResolvedName.of(composed, untranslated);
    }

    static List<String> arguments(String format, String actual) {
        List<Token> tokens = tokens(format);
        StringBuilder regex = new StringBuilder("^");
        List<Integer> groups = new ArrayList<>();
        int highest = -1;
        for (Token token : tokens) {
            if (token.position == LITERAL_PERCENT) {
                regex.append(Pattern.quote("%"));
            } else if (token.position != LITERAL) {
                regex.append("(.+?)");
                groups.add(token.position);
                highest = Math.max(highest, token.position);
            } else {
                regex.append(Pattern.quote(token.literal));
            }
        }
        if (groups.isEmpty()) {
            return null;
        }
        Matcher matcher = Pattern.compile(regex.append('$').toString(), Pattern.DOTALL).matcher(actual);
        if (!matcher.matches()) {
            return null;
        }
        List<String> arguments = new ArrayList<>();
        for (int i = 0; i <= highest; i++) {
            arguments.add(null);
        }
        for (int group = 0; group < groups.size(); group++) {
            int position = groups.get(group);
            String text = matcher.group(group + 1);
            String previous = arguments.set(position, text);
            if (previous != null && !previous.equals(text)) {
                return null;
            }
        }
        return arguments;
    }

    public static String render(String format, List<String> arguments) {
        StringBuilder out = new StringBuilder();
        for (Token token : tokens(format)) {
            if (token.position == LITERAL_PERCENT) {
                out.append('%');
            } else if (token.position >= 0) {
                if (token.position >= arguments.size() || arguments.get(token.position) == null) {
                    return null;
                }
                out.append(arguments.get(token.position));
            } else {
                out.append(token.literal);
            }
        }
        return out.toString();
    }

    private static List<Token> tokens(String format) {
        List<Token> tokens = new ArrayList<>();
        Matcher matcher = POSITION.matcher(format);
        int sequential = 0;
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last) {
                tokens.add(new Token(format.substring(last, matcher.start()), LITERAL));
            }
            if (matcher.group().equals("%%")) {
                tokens.add(new Token("%", LITERAL_PERCENT));
            } else if (matcher.group(1) != null) {
                tokens.add(new Token(null, Integer.parseInt(matcher.group(1)) - 1));
            } else {
                tokens.add(new Token(null, sequential++));
            }
            last = matcher.end();
        }
        if (last < format.length()) {
            tokens.add(new Token(format.substring(last), LITERAL));
        }
        return tokens;
    }

    private static final class Token {
        final String literal;
        final int position;

        Token(String literal, int position) {
            this.literal = literal;
            this.position = position;
        }
    }
}
