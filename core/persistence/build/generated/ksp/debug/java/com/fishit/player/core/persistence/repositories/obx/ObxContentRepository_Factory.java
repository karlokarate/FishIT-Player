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
public final class ObxContentRepository_Factory implements Factory<ObxContentRepository> {
  private final Provider<BoxStore> boxStoreProvider;

  public ObxContentRepository_Factory(Provider<BoxStore> boxStoreProvider) {
    this.boxStoreProvider = boxStoreProvider;
  }

  @Override
  public ObxContentRepository get() {
    return newInstance(boxStoreProvider.get());
  }

  public static ObxContentRepository_Factory create(Provider<BoxStore> boxStoreProvider) {
    return new ObxContentRepository_Factory(boxStoreProvider);
  }

  public static ObxContentRepository newInstance(BoxStore boxStore) {
    return new ObxContentRepository(boxStore);
  }
}
