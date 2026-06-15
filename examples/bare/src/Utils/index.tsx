import { stat } from '@dr.pogodin/react-native-fs';
export const getFullFilename = (path: string | null) => {
  if (typeof path === 'string') {
    let _path = path;

    // In case of url, check if it ends with "/" and do not consider it furthermore
    if (_path[_path.length - 1] === '/') _path = _path.substring(0, path.length - 1);

    const array = _path.split('/');
    return array.length > 1 ? array[array.length - 1] : '';
  }
  return '';
};

// RNFS `stat` expects a plain filesystem path. Picker URIs arrive as
// percent-encoded `file://` URLs (e.g. spaces become %20), which stat can't
// resolve. Strip the scheme and decode before delegating.
export const getFileInfo = (path: string) => {
  let _path = path.startsWith('file://') ? path.replace('file://', '') : path;
  try {
    _path = decodeURIComponent(_path);
  } catch {
    // leave path as-is if it isn't valid percent-encoding
  }
  return stat(_path);
};
