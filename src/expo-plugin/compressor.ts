import { ConfigPlugin, createRunOncePlugin } from '@expo/config-plugins';
const pkg = require('../../../package.json');

type Props = {};

const withCompressor: ConfigPlugin<Props> = (config) => config;

export default createRunOncePlugin(withCompressor, pkg.name, pkg.version);
