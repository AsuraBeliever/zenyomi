-keep class eu.kanade.tachiyomi.source.model.** { public protected *; }
-keep class eu.kanade.tachiyomi.source.online.** { public protected *; }
-keep class eu.kanade.tachiyomi.source.** extends eu.kanade.tachiyomi.source.Source { public protected *; }

-keep,allowoptimization class eu.kanade.tachiyomi.util.JsoupExtensionsKt { public protected *; }

# Anime counterpart of the rules above. Without these R8 finalises methods that no
# subclass inside the app overrides, getId() among them, and every anime extension then
# fails to load in release builds with:
#   LinkageError: Method ...getId() overrides final method in class AnimeHttpSource
# Plain -keep, not -keep,allowoptimization: the optimisation is exactly the problem.
-keep class eu.kanade.tachiyomi.animesource.model.** { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.online.** { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.** extends eu.kanade.tachiyomi.animesource.AnimeSource { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.AnimeSource { public protected *; }
