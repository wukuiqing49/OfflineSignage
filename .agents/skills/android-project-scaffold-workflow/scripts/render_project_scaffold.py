#!/usr/bin/env python3
"""Generate the complete Android project scaffold from project-scaffold.yml."""

from __future__ import annotations

import argparse
import hashlib
import html
import json
import os
import re
import shutil
import subprocess
from pathlib import Path

import yaml


TOKEN_PATTERN = re.compile(r"\{\{[A-Z0-9_]+\}\}")
SEMVER_PATTERN = re.compile(r"^(\d+)\.(\d+)\.(\d+)$")
TAG_PATTERN = re.compile(r"^v?(\d+)\.(\d+)\.(\d+)$")
MARKER_FILE = ".android-scaffold-generated.json"
MARKER_SCHEMA_VERSION = 3
SUPPORTED_LIBRARY_ALIASES = {
    "androidx-core-ktx",
    "androidx-activity-ktx",
    "androidx-appcompat",
    "androidx-fragment-ktx",
    "androidx-lifecycle-runtime-ktx",
    "androidx-lifecycle-viewmodel-ktx",
    "androidx-lifecycle-livedata-ktx",
    "androidx-constraintlayout",
    "androidx-recyclerview",
    "material",
    "wkq-core-base",
    "wkq-core-utils",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", required=True, type=Path)
    parser.add_argument("--target", required=True, type=Path)
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--wkq-base-version")
    parser.add_argument("--wkq-utils-version")
    return parser.parse_args()


def require(mapping: dict, key: str, context: str):
    value = mapping.get(key)
    if value is None or value == "":
        raise ValueError(f"{context} missing required key: {key}")
    return value


def require_mapping(mapping: dict, key: str, context: str) -> dict:
    value = require(mapping, key, context)
    if not isinstance(value, dict):
        raise ValueError(f"{context}.{key} must be a mapping")
    return value


def relative_path(value: str, context: str) -> Path:
    path = Path(value)
    if path.is_absolute() or ".." in path.parts:
        raise ValueError(f"{context} must be a relative path inside the target: {value}")
    return path


def gradle_project_path(module_path: str) -> str:
    parts = [part for part in re.split(r"[\\/]", module_path) if part]
    return ":" + ":".join(parts)


def package_path(package_name: str) -> Path:
    if not re.fullmatch(r"[A-Za-z_]\w*(\.[A-Za-z_]\w*)*", package_name):
        raise ValueError(f"Invalid package name: {package_name}")
    return Path(*package_name.split("."))


def resolve_application_class_name(project_name: str, package_name: str, configured: object) -> str:
    if configured:
        candidate = str(configured).strip()
    else:
        parts = re.findall(r"[A-Za-z0-9]+", project_name)
        if not parts:
            parts = [package_name.rsplit(".", 1)[-1]]
        base = "".join(part[:1].upper() + part[1:] for part in parts)
        if base[0].isdigit():
            base = "App" + base
        candidate = base + "Application"
    if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", candidate):
        raise ValueError(f"application.applicationClassName is not a valid class name: {candidate}")
    return candidate


def dependency_accessor(alias: str) -> str:
    return "libs." + alias.replace("-", ".")


def render_dependencies(entries: list, default_configuration: str = "implementation") -> str:
    lines: list[str] = []
    for entry in entries:
        if isinstance(entry, str):
            alias = entry
            configuration = default_configuration
        elif isinstance(entry, dict):
            alias = str(require(entry, "alias", "dependency"))
            configuration = str(entry.get("configuration", default_configuration))
        else:
            raise ValueError(f"Unsupported dependency entry: {entry!r}")
        if alias not in SUPPORTED_LIBRARY_ALIASES:
            raise ValueError(f"Dependency alias is not defined by the version catalog template: {alias}")
        if configuration not in {"api", "implementation", "compileOnly", "runtimeOnly"}:
            raise ValueError(f"Unsupported dependency configuration: {configuration}")
        lines.append(f"    {configuration} {dependency_accessor(alias)}")
    return "\n".join(lines)


def render(template: Path, values: dict[str, str]) -> str:
    content = template.read_text(encoding="utf-8")
    for token, value in values.items():
        content = content.replace("{{" + token + "}}", value)
    unresolved = sorted(set(TOKEN_PATTERN.findall(content)))
    if unresolved:
        raise ValueError(f"Unresolved template tokens in {template}: {unresolved}")
    return content


def content_digest(content: str | bytes) -> str:
    raw = content if isinstance(content, bytes) else content.encode("utf-8")
    return hashlib.sha256(raw).hexdigest()


def file_digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def latest_git_tag(repository: str) -> str:
    url = repository if repository.endswith(".git") else repository + ".git"
    result = subprocess.run(
        ["git", "ls-remote", "--tags", "--refs", url],
        check=True,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    candidates: list[tuple[tuple[int, int, int], str]] = []
    for line in result.stdout.splitlines():
        ref = line.rsplit("refs/tags/", 1)[-1]
        match = TAG_PATTERN.fullmatch(ref)
        if match:
            candidates.append((tuple(int(part) for part in match.groups()), ref))
    if not candidates:
        raise ValueError(f"No semantic version tag found for {repository}")
    return max(candidates)[1]


def xml_resource_name(resource_path: Path) -> str:
    if resource_path.suffix != ".xml":
        raise ValueError(f"Android XML resource must end with .xml: {resource_path}")
    return resource_path.stem


def validate_res_directory(value: str, context: str) -> str:
    if not re.fullmatch(r"[a-z0-9_]+(-[A-Za-z0-9_]+)*", value):
        raise ValueError(f"{context} is not a valid Android res directory name: {value}")
    return value


def configured_localized_value_dirs(resources: dict) -> list[str]:
    dirs = resources.get("localizedValueDirs", [])
    if dirs is None:
        return []
    if not isinstance(dirs, list):
        raise ValueError("resources.localizedValueDirs must be a list")
    result: list[str] = []
    for item in dirs:
        directory = validate_res_directory(str(item), "resources.localizedValueDirs")
        if not directory.startswith("values-"):
            raise ValueError("resources.localizedValueDirs entries must start with values-")
        if directory not in result:
            result.append(directory)
    return result


def add_resource_skeleton_outputs(
    outputs: dict[Path, str | bytes],
    module_path: Path,
    assets: Path,
    values: dict[str, str],
    prefix: str,
    localized_value_dirs: list[str],
    include_night: bool,
    include_drawable_night: bool,
) -> None:
    res_root = module_path / "src/main/res"
    outputs[res_root / "values/dimens.xml"] = (assets / f"{prefix}-dimens.xml").read_text(encoding="utf-8")
    outputs[res_root / "values/styles.xml"] = (assets / f"{prefix}-styles.xml").read_text(encoding="utf-8")
    outputs[res_root / "drawable/bg_scaffold_surface.xml"] = (
        assets / f"{prefix}-bg-scaffold-surface.xml"
    ).read_text(encoding="utf-8")

    for directory in localized_value_dirs:
        outputs[res_root / directory / "strings.xml"] = render(
            assets / f"{prefix}-localized-strings.xml.tmpl",
            values,
        )

    if include_night:
        outputs[res_root / "values-night/colors.xml"] = (
            assets / f"{prefix}-colors-night.xml"
        ).read_text(encoding="utf-8")
    if include_drawable_night:
        outputs[res_root / "drawable-night/bg_scaffold_surface.xml"] = (
            assets / f"{prefix}-bg-scaffold-surface-night.xml"
        ).read_text(encoding="utf-8")


def prepare_outputs(
    config: dict,
    assets: Path,
    base_version_override: str | None,
    utils_version_override: str | None,
) -> tuple[dict[Path, str | bytes], dict]:
    project_name = str(require(config, "projectName", "project config"))
    package_name = str(require(config, "packageName", "project config"))
    application_id = str(require(config, "applicationId", "project config"))
    if not project_name.strip() or "'" in project_name or "\n" in project_name or "\r" in project_name:
        raise ValueError("projectName must be a non-empty single-line value without single quotes")
    package_dir = package_path(package_name)
    package_path(application_id)

    gradle = require_mapping(config, "gradle", "project config")
    if gradle.get("dsl") != "groovy" or gradle.get("useKotlinDsl", False):
        raise ValueError("The bundled scaffold currently supports Groovy DSL only")
    settings_file = relative_path(str(require(gradle, "settingsFile", "gradle config")), "settingsFile")
    build_file_name = str(require(gradle, "buildFile", "gradle config"))
    if Path(build_file_name).name != build_file_name:
        raise ValueError("gradle.buildFile must be a file name")

    application = require_mapping(config, "application", "project config")
    if application.get("versionCodePolicy") != "semver-major-minor-patch":
        raise ValueError("Only semver-major-minor-patch versionCodePolicy is supported")
    if application.get("versionCodeFormula") != "major*1000000+minor*1000+patch":
        raise ValueError("Unsupported application.versionCodeFormula")
    version_name = str(require(application, "versionName", "application config"))
    version_match = SEMVER_PATTERN.fullmatch(version_name)
    if not version_match:
        raise ValueError("application.versionName must be major.minor.patch")
    major, minor, patch = (int(part) for part in version_match.groups())
    if minor > 999 or patch > 999:
        raise ValueError("version minor and patch must not exceed 999")
    version_code = major * 1_000_000 + minor * 1_000 + patch
    if not 1 <= version_code <= 2_100_000_000:
        raise ValueError("calculated versionCode is outside Google Play range")
    app_config_path = relative_path(
        str(require(application, "configFile", "application config")),
        "application.configFile",
    )
    app_conventions_path = relative_path(
        str(require(application, "conventionsFile", "application config")),
        "application.conventionsFile",
    )
    namespace_key = str(require(application, "namespaceKey", "application config"))
    application_id_key = str(require(application, "applicationIdKey", "application config"))
    version_name_key = str(require(application, "versionNameKey", "application config"))
    require_signing_key = str(require(application, "requireSigningKey", "application config"))
    application_class = resolve_application_class_name(
        project_name,
        package_name,
        application.get("applicationClassName"),
    )

    versions = require_mapping(config, "versions", "project config")
    if versions.get("policy") != "pinned-stable-baseline":
        raise ValueError("Only pinned-stable-baseline versions.policy is supported")
    modules = require_mapping(config, "modules", "project config")
    module_paths = {
        name: relative_path(str(require(modules, key, "modules config")), f"modules.{key}")
        for name, key in {
            "app": "app",
            "core_base": "coreBase",
            "core_utils": "coreUtils",
            "feature_app": "featureApp",
            "feature_res": "featureRes",
        }.items()
    }
    project_paths = {name: gradle_project_path(str(path)) for name, path in module_paths.items()}
    core_root = relative_path(str(require(modules, "coreRoot", "modules config")), "modules.coreRoot")
    feature_root = relative_path(str(require(modules, "featureRoot", "modules config")), "modules.featureRoot")
    if module_paths["core_base"].parent != core_root or module_paths["core_utils"].parent != core_root:
        raise ValueError("coreBase/coreUtils must be direct children of modules.coreRoot")
    if module_paths["feature_app"].parent != feature_root or module_paths["feature_res"].parent != feature_root:
        raise ValueError("featureApp/featureRes must be direct children of modules.featureRoot")

    language = require_mapping(config, "language", "project config")
    if not language.get("kotlin", False):
        raise ValueError("This scaffold requires language.kotlin=true")
    source_root = relative_path(str(language.get("sourceRoot", "src/main/java")), "language.sourceRoot")
    if len(source_root.parts) < 3 or source_root.parts[:2] != ("src", "main"):
        raise ValueError("language.sourceRoot must be inside src/main")

    native = require_mapping(config, "native", "project config")
    if native.get("abiPolicy") != "required-library-intersection":
        raise ValueError("Only required-library-intersection native.abiPolicy is supported")
    if not native.get("require16KbPageSize", False):
        raise ValueError("This scaffold requires native.require16KbPageSize=true")
    abis = require(native, "abis", "native config")
    if not isinstance(abis, list) or not abis:
        raise ValueError("native.abis must be a non-empty list")

    wkq = require_mapping(config, "wkq", "project config")
    if not wkq.get("enabled", False):
        raise ValueError("This scaffold requires wkq.enabled=true")
    if wkq.get("versionPolicy") != "latest-git-tag-at-integration":
        raise ValueError("Only latest-git-tag-at-integration wkq.versionPolicy is supported")
    if not wkq.get("requireBaseInheritance", False):
        raise ValueError("This scaffold requires wkq.requireBaseInheritance=true")
    if not wkq.get("initializeCoreUtilsInApplication", False):
        raise ValueError("This scaffold requires wkq.initializeCoreUtilsInApplication=true")
    wkq_base = require_mapping(wkq, "coreBase", "wkq config")
    wkq_utils = require_mapping(wkq, "coreUtils", "wkq config")
    base_version = base_version_override or latest_git_tag(str(require(wkq_base, "repository", "wkq.coreBase")))
    utils_version = utils_version_override or latest_git_tag(str(require(wkq_utils, "repository", "wkq.coreUtils")))

    release = require_mapping(config, "release", "project config")
    if not release.get("createProguardFiles", False):
        raise ValueError("This scaffold requires release.createProguardFiles=true")
    if not release.get("neverWriteSecrets", False):
        raise ValueError("This scaffold requires release.neverWriteSecrets=true")
    signing_environment = require_mapping(release, "signingEnvironment", "release config")
    signing_properties_path = relative_path(
        str(require(release, "signingPropertiesFile", "release config")),
        "release.signingPropertiesFile",
    )
    signing_keystore_path = relative_path(
        str(require(release, "signingKeystoreFile", "release config")),
        "release.signingKeystoreFile",
    )
    signing_properties_file = signing_properties_path.as_posix()
    if release.get("createSigningPlaceholder", False) or "signingPropertiesExampleFile" in release:
        raise ValueError(
            "Signing placeholders are no longer supported; provide signingPropertiesFile and signingKeystoreFile"
        )
    app_proguard_file = str(require(release, "appProguardFile", "release config"))
    library_consumer_file = str(require(release, "libraryConsumerRulesFile", "release config"))
    if Path(app_proguard_file).name != app_proguard_file:
        raise ValueError("release.appProguardFile must be a file name")
    if Path(library_consumer_file).name != library_consumer_file:
        raise ValueError("release.libraryConsumerRulesFile must be a file name")

    dependencies = require_mapping(config, "dependencies", "project config")
    if not dependencies.get("useVersionCatalog", False):
        raise ValueError("This scaffold requires dependencies.useVersionCatalog=true")
    if not dependencies.get("includeKotlinAndroidPlugin", False):
        raise ValueError("This scaffold requires dependencies.includeKotlinAndroidPlugin=true")
    if dependencies.get("featureRes"):
        raise ValueError("featureRes must remain dependency-free in this scaffold")

    compatibility = require_mapping(config, "androidCompatibility", "project config")
    network = require_mapping(compatibility, "networkSecurity", "androidCompatibility")
    provider = require_mapping(compatibility, "fileProvider", "androidCompatibility")
    network_enabled = bool(network.get("enabled", False))
    provider_enabled = bool(provider.get("enabled", False))
    if provider_enabled and not provider.get("exposeOnlySharedSubdirectories", False):
        raise ValueError("FileProvider must expose only shared subdirectories")

    network_resource = relative_path(str(require(network, "resource", "networkSecurity")), "networkSecurity.resource")
    provider_resource = relative_path(str(require(provider, "pathsResource", "fileProvider")), "fileProvider.pathsResource")
    app_xml_root = module_paths["app"] / "src/main/res/xml"
    if network_resource.parent != app_xml_root:
        raise ValueError("networkSecurity.resource must be inside the app src/main/res/xml directory")
    if provider_resource.parent != app_xml_root:
        raise ValueError("fileProvider.pathsResource must be inside the app src/main/res/xml directory")
    network_name = xml_resource_name(network_resource)
    provider_name = xml_resource_name(provider_resource)

    resources = config.get("resources", {})
    if resources is None:
        resources = {}
    if not isinstance(resources, dict):
        raise ValueError("resources must be a mapping")
    localized_value_dirs = configured_localized_value_dirs(resources)
    create_app_resource_skeleton = bool(resources.get("createAppResourceSkeleton", True))
    create_feature_res_skeleton = bool(resources.get("createFeatureResSkeleton", True))
    include_night_resources = bool(resources.get("includeNightResources", True))
    include_drawable_night_resources = bool(resources.get("includeDrawableNightResources", True))

    ui = require_mapping(config, "ui", "project config")
    rtl = require_mapping(ui, "rtl", "ui config")
    if not rtl.get("enabled", False):
        raise ValueError("ui.rtl.enabled must be true for generated projects")
    if not rtl.get("preferRelativeAttributes", False):
        raise ValueError("ui.rtl.preferRelativeAttributes must be true for generated projects")
    if not rtl.get("requireVisualValidationForDirectionalUi", False):
        raise ValueError("ui.rtl.requireVisualValidationForDirectionalUi must be true for generated projects")
    edge_to_edge = require_mapping(ui, "edgeToEdge", "ui config")
    edge_to_edge_enabled = bool(edge_to_edge.get("enabled", True))
    edge_to_edge_in_activity = edge_to_edge_enabled and bool(edge_to_edge.get("consumeInBaseActivity", True))
    if edge_to_edge.get("ordinaryPagesHideSystemBars", False):
        raise ValueError("ui.edgeToEdge.ordinaryPagesHideSystemBars must be false")
    if edge_to_edge_enabled and not edge_to_edge.get("fullScreenPagesRequireRestoreStrategy", False):
        raise ValueError("ui.edgeToEdge.fullScreenPagesRequireRestoreStrategy must be true")
    if edge_to_edge_enabled and not edge_to_edge.get("dialogPopupRequireWindowInsets", False):
        raise ValueError("ui.edgeToEdge.dialogPopupRequireWindowInsets must be true")

    core_base_namespace = package_name + ".core.base"
    core_utils_namespace = package_name + ".core.utils"
    feature_app_namespace = package_name + ".feature.app"
    feature_res_namespace = package_name + ".feature.res"

    network_permissions = ""
    network_attributes = ""
    if network_enabled:
        network_permissions = (
            '    <uses-permission android:name="android.permission.INTERNET" />\n'
            '    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />'
        )
        network_attributes = (
            f'        android:networkSecurityConfig="@xml/{network_name}"\n'
            f'        android:usesCleartextTraffic="{str(bool(network.get("cleartextTrafficPermitted", False))).lower()}"'
        )

    provider_block = ""
    if provider_enabled:
        authority = str(require(provider, "authority", "fileProvider config"))
        if "${applicationId}" not in authority:
            raise ValueError("fileProvider.authority must contain the ${applicationId} placeholder")
        provider_block = (
            '        <provider\n'
            '            android:name="androidx.core.content.FileProvider"\n'
            f'            android:authorities="{authority}"\n'
            '            android:exported="false"\n'
            '            android:grantUriPermissions="true">\n'
            '            <meta-data\n'
            '                android:name="android.support.FILE_PROVIDER_PATHS"\n'
            f'                android:resource="@xml/{provider_name}" />\n'
            '        </provider>'
        )

    values = {
        "PROJECT_NAME": project_name,
        "APP_NAME_XML": html.escape(project_name, quote=True),
        "PACKAGE_NAME": package_name,
        "APPLICATION_ID": application_id,
        "APPLICATION_CLASS": application_class,
        "APP_CONFIG_FILE": app_config_path.as_posix(),
        "APP_CONVENTIONS_FILE": app_conventions_path.as_posix(),
        "NAMESPACE_KEY": namespace_key,
        "APPLICATION_ID_KEY": application_id_key,
        "VERSION_NAME": version_name,
        "VERSION_NAME_KEY": version_name_key,
        "REQUIRE_SIGNING_KEY": require_signing_key,
        "REQUIRE_SIGNING_DEFAULT": str(bool(release.get("requireSigningByDefault", False))).lower(),
        "AGP_VERSION": str(require(versions, "agp", "versions config")),
        "GRADLE_VERSION": str(require(versions, "gradle", "versions config")),
        "KOTLIN_VERSION": str(require(versions, "kotlin", "versions config")),
        "COMPILE_SDK": str(require(versions, "compileSdk", "versions config")),
        "TARGET_SDK": str(require(versions, "targetSdk", "versions config")),
        "MIN_SDK": str(require(versions, "minSdk", "versions config")),
        "ANDROIDX_CORE_VERSION": str(require(versions, "androidxCore", "versions config")),
        "ANDROIDX_ACTIVITY_VERSION": str(require(versions, "androidxActivity", "versions config")),
        "ANDROIDX_APPCOMPAT_VERSION": str(require(versions, "androidxAppcompat", "versions config")),
        "ANDROIDX_FRAGMENT_VERSION": str(require(versions, "androidxFragment", "versions config")),
        "ANDROIDX_LIFECYCLE_VERSION": str(require(versions, "androidxLifecycle", "versions config")),
        "ANDROIDX_CONSTRAINTLAYOUT_VERSION": str(require(versions, "androidxConstraintlayout", "versions config")),
        "ANDROIDX_RECYCLERVIEW_VERSION": str(require(versions, "androidxRecyclerview", "versions config")),
        "MATERIAL_VERSION": str(require(versions, "material", "versions config")),
        "WKQ_CORE_BASE_VERSION": base_version,
        "WKQ_CORE_UTILS_VERSION": utils_version,
        "WKQ_CORE_BASE_COORDINATE": str(require(wkq_base, "coordinate", "wkq.coreBase")),
        "WKQ_CORE_UTILS_COORDINATE": str(require(wkq_utils, "coordinate", "wkq.coreUtils")),
        "APP_PROJECT": project_paths["app"],
        "CORE_BASE_PROJECT": project_paths["core_base"],
        "CORE_UTILS_PROJECT": project_paths["core_utils"],
        "FEATURE_APP_PROJECT": project_paths["feature_app"],
        "FEATURE_RES_PROJECT": project_paths["feature_res"],
        "NAMESPACE": package_name,
        "CORE_BASE_NAMESPACE": core_base_namespace,
        "CORE_UTILS_NAMESPACE": core_utils_namespace,
        "FEATURE_APP_NAMESPACE": feature_app_namespace,
        "FEATURE_RES_NAMESPACE": feature_res_namespace,
        "ABI_FILTERS": ", ".join(repr(str(abi)) for abi in abis),
        "MINIFY_ENABLED": str(bool(require(release, "minifyEnabled", "release config"))).lower(),
        "SHRINK_RESOURCES": str(bool(require(release, "shrinkResources", "release config"))).lower(),
        "APP_PROGUARD_FILE": app_proguard_file,
        "LIBRARY_CONSUMER_RULES_FILE": library_consumer_file,
        "SIGNING_PROPERTIES_FILE": signing_properties_file,
        "SIGNING_STORE_FILE_ENV": str(require(signing_environment, "storeFile", "signingEnvironment")),
        "SIGNING_STORE_PASSWORD_ENV": str(require(signing_environment, "storePassword", "signingEnvironment")),
        "SIGNING_KEY_ALIAS_ENV": str(require(signing_environment, "keyAlias", "signingEnvironment")),
        "SIGNING_KEY_PASSWORD_ENV": str(require(signing_environment, "keyPassword", "signingEnvironment")),
        "APP_DEPENDENCIES": render_dependencies(require(dependencies, "app", "dependencies config")),
        "CORE_BASE_DEPENDENCIES": render_dependencies(require(dependencies, "coreBase", "dependencies config")),
        "CORE_UTILS_DEPENDENCIES": render_dependencies(require(dependencies, "coreUtils", "dependencies config")),
        "FEATURE_APP_DEPENDENCIES": render_dependencies(require(dependencies, "featureApp", "dependencies config")),
        "NETWORK_PERMISSIONS": network_permissions,
        "NETWORK_ATTRIBUTES": network_attributes,
        "FILE_PROVIDER_BLOCK": provider_block,
        "SUPPORTS_RTL": str(bool(rtl["enabled"])).lower(),
        "EDGE_TO_EDGE_IMPORT": "import androidx.activity.enableEdgeToEdge\n" if edge_to_edge_in_activity else "",
        "EDGE_TO_EDGE_INIT": "        enableEdgeToEdge()\n" if edge_to_edge_in_activity else "",
        "LIGHT_STATUS_BAR": str(bool(edge_to_edge.get("lightStatusBar", True))).lower(),
        "LIGHT_NAVIGATION_BAR": str(bool(edge_to_edge.get("lightNavigationBar", True))).lower(),
        "NAVIGATION_BAR_CONTRAST_ENFORCED": str(bool(edge_to_edge.get("navigationBarContrastEnforced", False))).lower(),
        "THEME_NAME": f"Theme.{application_class.removesuffix('Application')}",
    }

    outputs: dict[Path, str | bytes] = {
        settings_file: render(assets / "settings.gradle.groovy.tmpl", values),
        Path(build_file_name): (assets / "root-build.gradle.groovy").read_text(encoding="utf-8"),
        Path("gradle.properties"): (assets / "gradle.properties.root.tmpl").read_text(encoding="utf-8"),
        app_config_path: render(assets / "app-config.properties.tmpl", values),
        app_conventions_path: render(assets / "app-conventions.gradle.groovy.tmpl", values),
        Path("gradle/libs.versions.toml"): render(assets / "libs.versions.toml.tmpl", values),
        Path("gradle/wrapper/gradle-wrapper.properties"): render(assets / "gradle-wrapper.properties.tmpl", values),
        Path("gradle/wrapper/gradle-wrapper.jar"): (assets / "wrapper/gradle-wrapper.jar").read_bytes(),
        Path("gradlew"): (assets / "wrapper/gradlew").read_bytes(),
        Path("gradlew.bat"): (assets / "wrapper/gradlew.bat").read_bytes(),
        module_paths["app"] / build_file_name: render(assets / "app-build.gradle.groovy.tmpl", values),
        module_paths["app"] / "src/main/AndroidManifest.xml": render(assets / "AndroidManifest.xml.tmpl", values),
        module_paths["app"] / source_root / package_dir / "MainActivity.kt": render(assets / "MainActivity.kt.tmpl", values),
        module_paths["app"] / source_root / package_dir / f"{application_class}.kt": render(assets / "Application.kt.tmpl", values),
        module_paths["app"] / "src/main/res/layout/activity_main.xml": (assets / "activity_main.xml").read_text(encoding="utf-8"),
        module_paths["app"] / "src/main/res/values/strings.xml": render(assets / "strings.xml.tmpl", values),
        module_paths["app"] / "src/main/res/values/colors.xml": (assets / "colors.xml").read_text(encoding="utf-8"),
        module_paths["app"] / "src/main/res/values/themes.xml": render(assets / "themes.xml", values),
        module_paths["app"] / app_proguard_file: (assets / "app-proguard-rules.pro").read_text(encoding="utf-8"),
        module_paths["core_base"] / build_file_name: render(assets / "core-base-build.gradle.groovy.tmpl", values),
        module_paths["core_utils"] / build_file_name: render(assets / "core-utils-build.gradle.groovy.tmpl", values),
        module_paths["feature_app"] / build_file_name: render(assets / "feature-app-build.gradle.groovy.tmpl", values),
        module_paths["feature_res"] / build_file_name: render(assets / "feature-res-build.gradle.groovy.tmpl", values),
        module_paths["feature_app"] / source_root / package_path(feature_app_namespace) / "FeatureAppEntry.kt": render(assets / "FeatureAppEntry.kt.tmpl", values),
    }

    for module_name in ("core_base", "core_utils", "feature_app", "feature_res"):
        module = module_paths[module_name]
        outputs[module / "src/main/AndroidManifest.xml"] = (assets / "library-AndroidManifest.xml").read_text(encoding="utf-8")
        outputs[module / library_consumer_file] = (assets / "library-consumer-rules.pro").read_text(encoding="utf-8")

    if create_app_resource_skeleton:
        add_resource_skeleton_outputs(
            outputs,
            module_paths["app"],
            assets,
            values,
            "app",
            localized_value_dirs,
            include_night_resources,
            include_drawable_night_resources,
        )

    if create_feature_res_skeleton:
        outputs[module_paths["feature_res"] / "src/main/res/values/strings.xml"] = render(
            assets / "feature-res-strings.xml.tmpl",
            values,
        )
        outputs[module_paths["feature_res"] / "src/main/res/values/colors.xml"] = (
            assets / "feature-res-colors.xml"
        ).read_text(encoding="utf-8")
        add_resource_skeleton_outputs(
            outputs,
            module_paths["feature_res"],
            assets,
            values,
            "feature-res",
            localized_value_dirs,
            include_night_resources,
            include_drawable_night_resources,
        )

    if network_enabled:
        trust_anchors = '            <certificates src="system" />'
        if not bool(network.get("trustSystemCertificatesOnly", True)):
            trust_anchors += '\n            <certificates src="user" />'
        network_values = {
            "CLEARTEXT_TRAFFIC_PERMITTED": str(bool(network.get("cleartextTrafficPermitted", False))).lower(),
            "TRUST_ANCHORS": trust_anchors,
        }
        outputs[network_resource] = render(assets / "network_security_config.xml", network_values)

    if provider_enabled:
        shared_subdirectory = str(require(provider, "sharedSubdirectory", "fileProvider config"))
        if shared_subdirectory.startswith(("/", "\\")) or ".." in Path(shared_subdirectory).parts:
            raise ValueError("fileProvider.sharedSubdirectory must be a safe relative directory")
        shared_subdirectory = shared_subdirectory.replace("\\", "/").strip("/")
        if not shared_subdirectory or shared_subdirectory == ".":
            raise ValueError("fileProvider.sharedSubdirectory must name a restricted child directory")
        shared_subdirectory += "/"
        outputs[provider_resource] = render(
            assets / "file_paths.xml",
            {"SHARED_SUBDIRECTORY": shared_subdirectory},
        )

    gitignore_lines = [
        ".gradle/", "build/", "local.properties", "*.iml", "__pycache__/", "*.pyc", "", "app/build/",
        "core/*/build/", "feature/*/build/", ".ai-work/",
    ]
    outputs[Path(".gitignore")] = "\n".join(gitignore_lines).rstrip() + "\n"

    generated_paths = sorted({relative.as_posix() for relative in outputs} | {MARKER_FILE})
    generated_digests = {
        relative.as_posix(): content_digest(content)
        for relative, content in sorted(outputs.items(), key=lambda item: item[0].as_posix())
    }
    metadata = {
        "schemaVersion": MARKER_SCHEMA_VERSION,
        "generator": "android-project-scaffold-workflow",
        "projectName": project_name,
        "applicationId": application_id,
        "applicationClass": application_class,
        "versionName": version_name,
        "versionCode": version_code,
        "wkqCoreBase": base_version,
        "wkqCoreUtils": utils_version,
        "edgeToEdgeEnabled": edge_to_edge_enabled,
        "edgeToEdgeInActivity": edge_to_edge_in_activity,
        "rtlEnabled": True,
        "rtlPreferRelativeAttributes": True,
        "rtlRequireVisualValidationForDirectionalUi": True,
        "generatedFiles": len(generated_paths),
        "generatedPaths": generated_paths,
        "generatedDigests": generated_digests,
        "preservedPaths": [
            app_config_path.as_posix(),
            signing_properties_path.as_posix(),
            signing_keystore_path.as_posix(),
        ],
        "signingPropertiesFile": signing_properties_path.as_posix(),
        "signingKeystoreFile": signing_keystore_path.as_posix(),
        "sourceRoot": source_root.as_posix(),
        "require16KbPageSize": True,
    }
    outputs[Path(MARKER_FILE)] = json.dumps(metadata, ensure_ascii=False, indent=2) + "\n"
    return outputs, metadata


def write_outputs(target: Path, outputs: dict[Path, str | bytes], force: bool) -> None:
    target.mkdir(parents=True, exist_ok=True)
    marker = target / MARKER_FILE
    previous_paths: set[Path] = set()
    protected_paths: set[Path] = set()
    if force:
        if not marker.is_file():
            raise FileExistsError(f"--force requires an existing {MARKER_FILE} in the target")
        previous_metadata = json.loads(marker.read_text(encoding="utf-8"))
        previous_paths = {
            relative_path(str(value), f"{MARKER_FILE}.generatedPaths")
            for value in previous_metadata.get("generatedPaths", [])
        }
        previous_digests = previous_metadata.get("generatedDigests") or {}
        if not isinstance(previous_digests, dict):
            raise ValueError(f"{MARKER_FILE}.generatedDigests must be a mapping")
        if previous_digests:
            for relative in previous_paths:
                destination = target / relative
                expected_digest = previous_digests.get(relative.as_posix())
                if destination.is_file() and (
                    not expected_digest or file_digest(destination) != expected_digest
                ):
                    protected_paths.add(relative)
        else:
            protected_paths.update(
                relative for relative in previous_paths
                if relative != Path(MARKER_FILE) and (target / relative).is_file()
            )
            print(f"WARN: legacy {MARKER_FILE} has no generatedDigests; preserving existing generated files")
        protected_paths.update({
            relative_path(str(value), f"{MARKER_FILE}.preservedPaths")
            for value in previous_metadata.get("preservedPaths", [])
        })
        legacy_local_config = previous_metadata.get("localConfigPath")
        if legacy_local_config:
            protected_paths.add(relative_path(str(legacy_local_config), f"{MARKER_FILE}.localConfigPath"))
        protected_paths.discard(Path(MARKER_FILE))
    if not force:
        existing = [str(path) for relative in outputs if (path := target / relative).exists()]
        if existing:
            raise FileExistsError(
                "Refusing to overwrite existing generated files:\n- "
                + "\n- ".join(existing[:20])
                + ("\n- ..." if len(existing) > 20 else "")
            )

    for relative, content in outputs.items():
        destination = target / relative
        if force and relative in protected_paths and destination.is_file():
            continue
        destination.parent.mkdir(parents=True, exist_ok=True)
        if isinstance(content, bytes):
            destination.write_bytes(content)
        else:
            destination.write_text(content, encoding="utf-8", newline="\n")
    if force:
        stale_paths = previous_paths - set(outputs) - protected_paths
        for relative in stale_paths:
            stale = target / relative
            if stale.is_file():
                stale.unlink()
    if os.name != "nt":
        (target / "gradlew").chmod(0o755)


def main() -> None:
    args = parse_args()
    skill_dir = Path(__file__).resolve().parent.parent
    config = yaml.safe_load(args.config.read_text(encoding="utf-8"))
    outputs, metadata = prepare_outputs(
        config,
        skill_dir / "assets" / "scaffold",
        args.wkq_base_version,
        args.wkq_utils_version,
    )
    write_outputs(args.target.resolve(), outputs, args.force)
    print(
        f"Generated {metadata['generatedFiles']} files for {metadata['projectName']} "
        f"with applicationId={metadata['applicationId']}, "
        f"versionName={metadata['versionName']}, versionCode={metadata['versionCode']}, "
        f"AndroidCoreBase={metadata['wkqCoreBase']}, AndroidCoreUtils={metadata['wkqCoreUtils']}"
    )


if __name__ == "__main__":
    main()
