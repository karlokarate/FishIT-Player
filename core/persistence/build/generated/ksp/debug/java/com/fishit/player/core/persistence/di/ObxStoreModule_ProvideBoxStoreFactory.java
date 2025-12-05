package com.fishit.player.core.persistence.di;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import io.objectbox.BoxStore;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class ObxStoreModule_ProvideBoxStoreFactory implements Factory<BoxStore> {
  private final Provider<Context> contextProvider;

  public ObxStoreModule_ProvideBoxStoreFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public BoxStore get() {
    return provideBoxStore(contextProvider.get());
  }

  public static ObxStoreModule_ProvideBoxStoreFactory create(Provider<Context> contextProvider) {
    return new ObxStoreModule_ProvideBoxStoreFactory(contextProvider);
  }

  public static BoxStore provideBoxStore(Context context) {
    return Preconditions.checkNotNullFromProvides(ObxStoreModule.INSTANCE.provideBoxStore(context));
  }
}
