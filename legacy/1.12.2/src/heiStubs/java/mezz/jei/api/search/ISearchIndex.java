package mezz.jei.api.search;

public interface ISearchIndex<T> {
    void put(String key, T value);
}
