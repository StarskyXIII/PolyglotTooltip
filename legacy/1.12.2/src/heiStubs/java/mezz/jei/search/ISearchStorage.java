package mezz.jei.search;

public interface ISearchStorage<T> {
    void put(String key, T value);
}
