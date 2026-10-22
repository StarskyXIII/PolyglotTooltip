package mezz.jei.search;

import java.util.Map;

public abstract class ElementSearch implements IElementSearch {
    private Map<PrefixInfo, PrefixedSearchable> prefixedSearchables;
}
