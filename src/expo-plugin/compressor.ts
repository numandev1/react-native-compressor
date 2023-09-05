//@ts-ignore
import { ConfigPlugin, createRunOncePlugin } from '@expo/config-plugins';
const pkg = require('../../../package.json');

type Props = {};

const withCompressor: ConfigPlugin<Props> = (config: any) => config;

export default createRunOncePlugin(withCompressor, pkg.name, pkg.version);
