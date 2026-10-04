package ent.kz.entbackend.platform.materials;

public interface StorageService {
  void put(String key, byte[] bytes, String mime);
  byte[] get(String key);
  void delete(String key);
}
