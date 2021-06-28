const RNFS = require('react-native-fs');
export const getFullFilename = (path: string | null) => {
  if (typeof path === 'string') {
    let _path = path;

    // In case of url, check if it ends with "/" and do not consider it furthermore
    if (_path[_path.length - 1] === '/')
      _path = _path.substring(0, path.length - 1);

    const array = _path.split('/');
    return array.length > 1 ? array[array.length - 1] : '';
  }
  return '';
};

export const getFileInfo = RNFS.stat;
