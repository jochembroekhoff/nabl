plugins {
  id("org.metaborg.devenv.spoofax.gradle.langspec")
  `maven-publish`
}

// Replace source dependencies with overridden/local ones.
fun compositeBuild(name: String) = "$group:$name:$version"
val spoofax2Version: String by ext
spoofaxLanguageSpecification {
  addSourceDependenciesFromMetaborgYaml.set(false)
}
dependencies {
  sourceLanguage(compositeBuild("meta.lib.spoofax"))
  sourceLanguage(project(":org.metaborg.meta.nabl2.shared"))
}
