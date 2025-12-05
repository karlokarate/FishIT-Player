package com.fishit.player.core.persistence.repositories.obx;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import io.objectbox.BoxStore;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation"
})
public final class ObxScreenTimeRepository_Factory implements Factory<ObxScreenTimeRepository> {
  private final Provider<BoxStore> boxStoreProvider;

  public ObxScreenTimeRepository_Factory(Provider<BoxStore> boxStoreProvider) {
    this.boxStoreProvider = boxStoreProvider;
  }

  @Override
  public ObxScreenTimeRepository get() {
    return newInstance(boxStoreProvider.get());
  }

  public static ObxScreenTimeRepository_Factory create(Provider<BoxStore> boxStoreProvider) {
    return new ObxScreenTimeRepository_Factory(boxStoreProvider);
  }

  public static ObxScreenTimeRepository newInstance(BoxStore boxStore) {
    return new ObxScreenTimeRepository(boxStore);
  }
}
