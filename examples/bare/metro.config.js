const path = require('path');
const { getDefaultConfig } = require('@react-native/metro-config');
const { withMetroConfig } = require('react-native-monorepo-config');
const baseJSBundleModule = require('metro/private/DeltaBundler/Serializers/baseJSBundle');
const bundleToStringModule = require('metro/private/lib/bundleToString');
const getAllFilesModule = require('metro/private/DeltaBundler/Serializers/getAllFiles');
const baseJSBundle = baseJSBundleModule.default ?? baseJSBundleModule;
const bundleToString = bundleToStringModule.default ?? bundleToStringModule;
const getAllFiles = getAllFilesModule.default ?? getAllFilesModule;

const root = path.resolve(__dirname, '..', '..');

/**
 * Metro configuration
 * https://facebook.github.io/metro/docs/configuration
 *
 * @type {import('metro-config').MetroConfig}
 */
const config = withMetroConfig(getDefaultConfig(__dirname), {
  root,
  dirname: __dirname,
});

config.resolver.useWatchman = false;
config.resolver.extraNodeModules = {
  ...config.resolver.extraNodeModules,
  '@dr.pogodin/react-native-fs': path.dirname(
    require.resolve('@dr.pogodin/react-native-fs/package.json')
  ),
};

const upstreamSerializer = config.serializer;
let mainEntryPointModules = new Set();

const serializeBundle = (entryPoint, preModules, graph, options) => {
  const serializedBundle = bundleToString(
    baseJSBundle(entryPoint, preModules, graph, options)
  );

  return typeof serializedBundle === 'string'
    ? serializedBundle
    : serializedBundle.code;
};

config.serializer = {
  ...upstreamSerializer,
  customSerializer: async (entryPoint, preModules, graph, options) => {
    if (options.modulesOnly) {
      return serializeBundle(entryPoint, preModules, graph, {
        ...options,
        processModuleFilter: (module) => {
          if (options.processModuleFilter && !options.processModuleFilter(module)) {
            return false;
          }

          return !mainEntryPointModules.has(module.path);
        },
      });
    }

    mainEntryPointModules = new Set(
      await getAllFiles(preModules, graph, options)
    );

    const bundle = serializeBundle(entryPoint, preModules, graph, options);
    const setupModuleMatch = bundle.match(
      /},(\d+),\[[^\]]*\],"[^"]*InitializeCore\.js"\);/
    );

    if (!setupModuleMatch) {
      return bundle;
    }

    return bundle.replace(
      /__r\((\d+)\);\s*\/\/# sourceMappingURL/,
      `__r(${setupModuleMatch[1]});\n__r($1);\n//# sourceMappingURL`
    );
  },
};

module.exports = config;
