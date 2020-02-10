import groovy.json.JsonSlurper
import org.gradle.util.VersionNumber

import java.util.regex.Pattern

def generatedFilePackage = "org.unimodules.adapters.react"
ext.generatedSrcDir = "generated/unimodules/src/main/java"
def generatedFileName = "UnimodulesPackageList.java"

def generatedUnimodulesPackageListTemplate = """
package $generatedFilePackage;

import java.util.Arrays;
import java.util.List;
import org.unimodules.core.interfaces.Package;

public class UnimodulesPackageList {
  public List<Package> getPackageList() {
    return Arrays.<Package>asList(
      {{ unimodulesPackages }}
    );
  }
}
"""

ext.sharedConfiguration = null
ext.sharedUnimodules = null

class Unimodule {
  String name
  List platforms
  List targets
  List androidPackages
  String directory
  String version
  String androidGroup
  String androidSubdirectory

  boolean supportsPlatform(String platform) {
    return platforms instanceof List && platforms.contains(platform)
  }

  boolean supportsTarget(String target) {
    return targets.size() == 0 || targets.contains(target)
  }
}

def readPackageFromJavaOrKotlinFile(String filePath) {
  def file = new File(filePath)
  def fileReader = new BufferedReader(new FileReader(file))
  def fileContent = ""
  while ((fileContent = fileReader.readLine()) != null) {
    def match = fileContent =~ /^package ([0-9a-zA-Z._]*);?$/
    if (match.size() == 1 && match[0].size() == 2) {
      fileReader.close()
      return match[0][1]
    }
  }
  fileReader.close()

  throw new GradleException("Java or Kotlin file $file does not include package declaration")
}

def readFromBuildGradle(String file) {
  def gradleFile = new File(file)
  if (!gradleFile.exists()) {
    return [:]
  }
  def fileReader = new BufferedReader(new FileReader(gradleFile))
  def result = [:]
  for (def line = fileReader.readLine(); line != null; line = fileReader.readLine()) {
    def versionMatch = line.trim() =~ /^version ?= ?'([\w.-]+)'$/
    def groupMatch = line.trim() =~ /^group ?= ?'([\w.]+)'$/
    if (versionMatch.size() == 1 && versionMatch[0].size() == 2) {
      result.version = versionMatch[0][1]
    }
    if (groupMatch.size() == 1 && groupMatch[0].size() == 2) {
      result.group = groupMatch[0][1]
    }
  }
  fileReader.close()
  return result
}

def findDefaultBasePackage(String packageDir) {
  def pathsJava = new FileNameFinder().getFileNames(packageDir, "android/src/**/*Package.java")
  def pathsKt = new FileNameFinder().getFileNames(packageDir, "android/src/**/*Package.kt")
  def paths = pathsJava + pathsKt

  if (paths.size != 1) {
    return []
  }

  def packageName = readPackageFromJavaOrKotlinFile(paths[0])
  def className = new File(paths[0]).getName().split(Pattern.quote("."))[0]
  return ["$packageName.$className"]
}

def getConfig(String configKey, fallback) {
  if (sharedConfiguration != null) {
    return sharedConfiguration.containsKey(configKey) ? sharedConfiguration.get(configKey) : fallback
  }
  return fallback
}

ext.generateUnimodulesPackageList = {
  def results = findUnimodules()
  def unimodules = results.unimodules
  def duplicates = results.duplicates

  def unimodulesPackagesBuilder = new StringBuilder()
  def isEmptyList = true

  for (unimodule in unimodules) {
    for (pkg in unimodule.androidPackages) {
      unimodulesPackagesBuilder.append("        new $pkg(),\n")
      isEmptyList = false
    }
  }
  if (!isEmptyList) {
    unimodulesPackagesBuilder.deleteCharAt(unimodulesPackagesBuilder.length() - 2) // remove last comma in a list
  }

  def generatedContents = generatedUnimodulesPackageListTemplate
    .replace("{{ unimodulesPackages }}", unimodulesPackagesBuilder.toString())

  def generatedCodeDir = new File(new File(project.buildDir, generatedSrcDir), generatedFilePackage.replace('.', '/'))
  def generatedJavaFile = new File(generatedCodeDir, generatedFileName)

  generatedCodeDir.mkdirs()
  generatedJavaFile.createNewFile()

  def javaFileWriter = new BufferedWriter(new FileWriter(generatedJavaFile))
  javaFileWriter.write(generatedContents)
  javaFileWriter.close()
}

def generateBasePackageList(List<Unimodule> unimodules) {
  def findMainJavaApp = new FileNameFinder().getFileNames(rootProject.getProjectDir().getPath(), '**/MainApplication.java', '')
  def findMainKtApp = new FileNameFinder().getFileNames(rootProject.getProjectDir().getPath(), '**/MainApplication.kt', '')
  
  if (findMainJavaApp.size() != 1 && findMainKtApp.size() != 1) {
    throw new GradleException("You need to have MainApplication in your project")
  }

  def findMainApp = (findMainJavaApp.size() == 1) ? findMainJavaApp : findMainKtApp
  def mainAppDirectory = new File(findMainApp[0]).parentFile
  def packageName = readPackageFromJavaOrKotlinFile(findMainApp[0])

  def fileBuilder = new StringBuilder()
  fileBuilder.append("package ${packageName}.generated;\n\n")

  fileBuilder.append("import java.util.Arrays;\n")
  fileBuilder.append("import java.util.List;\n")
  fileBuilder.append("import org.unimodules.core.interfaces.Package;\n\n")

  fileBuilder.append("public class BasePackageList {\n")
  fileBuilder.append("  public List<Package> getPackageList() {\n")
  fileBuilder.append("    return Arrays.<Package>asList(\n")
  def isEmptyList = true
  for (module in unimodules) {
    for (pkg in module.androidPackages) {
      fileBuilder.append("        new $pkg(),\n")
      isEmptyList = false
    }
  }
  if (!isEmptyList) {
    fileBuilder.deleteCharAt(fileBuilder.length() - 2) // remove last comma in a list
  }
  fileBuilder.append("    );\n")
  fileBuilder.append("  }\n")
  fileBuilder.append("}\n")

  new File(mainAppDirectory, "generated").mkdirs()
  def javaFile = new File(mainAppDirectory, "generated/BasePackageList.java")
  javaFile.createNewFile()
  def javaFileWriter = new BufferedWriter(new FileWriter(javaFile))
  javaFileWriter.write(fileBuilder.toString())
  javaFileWriter.close()
}

def findUnimodules() {
  if (ext.sharedUnimodules != null) {
    println "shared unimodules found"
    return ext.sharedUnimodules
  }

  println "shared unimodules not found"

  def exclude = getConfig("exclude", [])
  def modulesPaths = getConfig("modulesPaths", ['../../node_modules'])

  def unimodules = [:]
  def unimodulesDuplicates = []

  for (modulesPath in modulesPaths) {
    def baseDir = new File(rootProject.getBuildFile(), modulesPath).toString()
    def moduleConfigPaths = new FileNameFinder().getFileNames(baseDir, '**/unimodule.json', '')

    for (moduleConfigPath in moduleConfigPaths) {
      def unimoduleConfig = new File(moduleConfigPath)
      def unimoduleJson = new JsonSlurper().parseText(unimoduleConfig.text)
      def directory = unimoduleConfig.getParent()
      def buildGradle = readFromBuildGradle(new File(directory, "android/build.gradle").toString())
      def packageJsonFile = new File(directory, 'package.json')
      def packageJson = new JsonSlurper().parseText(packageJsonFile.text)

      def unimodule = new Unimodule()
      unimodule.name = unimoduleJson.name ?: packageJson.name
      unimodule.directory = directory
      unimodule.version = buildGradle.version ?: packageJson.version ?: "UNVERSIONED"
      unimodule.androidGroup = buildGradle.group ?: "org.unimodules"
      unimodule.androidSubdirectory = unimoduleJson.android?.subdirectory ?: "android"
      unimodule.platforms = unimoduleJson.platforms != null ? unimoduleJson.platforms : []
      assert unimodule.platforms instanceof List
      unimodule.targets = unimoduleJson.targets != null ? unimoduleJson.targets : []
      assert unimodule.targets instanceof List
      unimodule.androidPackages = unimoduleJson.android?.packages != null ?
          unimoduleJson.android.packages : findDefaultBasePackage(directory)
      assert unimodule.androidPackages instanceof List

      if (unimodule.supportsPlatform('android') && unimodule.supportsTarget("react-native")) {
        if (!exclude.contains(unimodule.name) && unimodule.name != 'unimodules-react-native-adapter') {
          if (unimodules[unimodule.name]) {
            unimodulesDuplicates.add(unimodule.name)
          }

          if (!unimodules[unimodule.name] ||
              VersionNumber.parse(unimodule.version) >= VersionNumber.parse(unimodules[unimodule.name].version)) {
            unimodules[unimodule.name] = unimodule
          }
        }
      }
    }
  }

  sharedUnimodules = [
      unimodules: unimodules.collect { entry -> entry.value },
      duplicates: unimodulesDuplicates.unique()
  ]
  return sharedUnimodules
}

def getUnimodule(String unimoduleName) {
  def unimodules = findUnimodules().unimodules
  return unimodules.find { it.name == unimoduleName }
}

class Colors {
  static final String NORMAL = "\u001B[0m"
  static final String RED = "\u001B[31m"
  static final String GREEN = "\u001B[32m"
  static final String YELLOW = "\u001B[33m"
  static final String MAGENTA = "\u001B[35m"
}

def addDependency(Project project, Object dependency, Closure closure = {}) {
  String configuration = getConfig("configuration", project.configurations.findByName("api") ? "api" : "compile")
  project.dependencies.add(configuration, dependency, closure)
}

def addDependencies(Project project, Closure<String> projectNameResolver) {
  def results = findUnimodules()
  def unimodules = results.unimodules
  def duplicates = results.duplicates

  if (unimodules.size() > 0) {
    println()
    println Colors.YELLOW + 'Installing unimodules:' + Colors.NORMAL
    for (unimodule in unimodules) {
      println ' ' + Colors.GREEN + unimodule.name + Colors.YELLOW + '@' + Colors.RED + unimodule.version + Colors.NORMAL + ' from ' + Colors.MAGENTA + unimodule.directory + Colors.NORMAL
      addDependency(project, projectNameResolver(unimodule))
    }

    if (duplicates.size() > 0) {
      println()
      println Colors.YELLOW + 'Found some duplicated unimodule packages. Installed the ones with the highest version number.' + Colors.NORMAL
      println Colors.YELLOW + 'Make sure following dependencies of your project are resolving to one specific version:' + Colors.NORMAL

      println ' ' + duplicates
          .collect { unimoduleName -> Colors.GREEN + unimoduleName + Colors.NORMAL }
          .join(', ')
    }
  } else {
    println()
    println Colors.YELLOW + "No unimodules found. Are you sure you've installed JS dependencies?" + Colors.NORMAL
  }
}

if (rootProject instanceof ProjectDescriptor) {
  // This block runs only when file is imported by `settings.gradle`.
  // In `settings.gradle`, projects are instances of `ProjectDescriptor`
  // from which `Project` instances are created and passed to `build.gradle`

  // These two methods below need to be available globally, so we define them in `gradle.ext`
  // as they are used in `build.gradle` and only `gradle` object is shared in those contexts.
  gradle.ext.addUnimodulesDependencies = { Project project ->
    addDependencies(project) { unimodule -> project.project(":${unimodule.name}") }
  }

  gradle.ext.addMavenUnimodulesDependencies = { Project project ->
    addDependencies(project) { unimodule -> "${unimodule.androidGroup}:${unimodule.name}:${unimodule.version}" }
  }

  // This is intended to be used only in `settings.gradle` so we scope it just for this context.
  ext.includeUnimodulesProjects = { Map options = [:] ->
    ext.sharedConfiguration = options
    def unimodules = findUnimodules().unimodules

    for (unimodule in unimodules) {
      include ":${unimodule.name}"
      project(":${unimodule.name}").projectDir = new File(unimodule.directory, unimodule.androidSubdirectory)
    }
  }
} else {
  // This block runs when file is not imported by `settings.gradle`.
  // 
  ext.unimodule = { String unimoduleName, Closure closure = null ->
    Object dependency = null;

    if (new File(project.rootProject.projectDir.parentFile, 'package.json').exists()) {
        // Parent directory of the android project has package.json -- probably React Native
        def unimodule = getUnimodule(unimoduleName)

        if (unimodule == null) {
          throw new GradleException("Unimodule with name '$unimoduleName' not found.")
        }

        dependency = project.project(":${unimodule.name}")
    } else {
        // There's no package.json and no pubspec.yaml
        throw new GradleException(
            "'unimodules-core.gradle' used in a project that seems to be neither a Flutter nor a React Native project."
        )
    }
    addDependency(dependency, closure)
  }
}
