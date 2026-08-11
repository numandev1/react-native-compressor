import { useState, useRef, useEffect } from 'react';
import { View, StyleSheet, Alert, useWindowDimensions, Image as RNImage, Platform, ScrollView } from 'react-native';
import Button from '../../Components/Button';
import Row from '../../Components/Row';
import MetaDataList from '../../Components/MetaDataList';
import ProgressBar from '../../Components/ProgressBar';
import type { ProgressBarRafType } from '../../Components/ProgressBar';
import * as ImagePicker from 'react-native-image-picker';
import { CameraRoll } from '@react-native-camera-roll/camera-roll';
import prettyBytes from 'pretty-bytes';
import { Image, getFileSize, getImageMetaData } from 'react-native-compressor';
import type { ImageMetaData } from 'react-native-compressor';
import { getFileInfo } from '../../Utils';

// Groups the response the way the docs describe it: geometry, then file, then the optional
// decoder/EXIF blocks. Anything not listed still renders, appended alphabetically.
const IMAGE_FIELD_ORDER = [
  'fileName',
  'extension',
  'mimeType',
  'size',
  'width',
  'height',
  'displayWidth',
  'displayHeight',
  'rotation',
  'isMirrored',
  'orientation',
  'hasAlpha',
  'isAnimated',
  'frameCount',
  'bitDepth',
  'colorSpace',
  'isHDR',
  'dpiX',
  'dpiY',
  'latitude',
  'longitude',
  'altitude',
  'make',
  'model',
  'lensModel',
  'software',
  'iso',
  'exposureTime',
  'focalLength',
  'flash',
];

const Index = () => {
  const progressRef = useRef<ProgressBarRafType>(null);
  const dimension = useWindowDimensions();
  const [orignalUri, setOrignalUri] = useState<string>();
  const [commpressedUri, setCommpressedUri] = useState<string>();
  const [fileName, setFileName] = useState<any>('');
  const [mimeType, setMimeType] = useState<any>('');
  const [orignalSize, setOrignalSize] = useState<string>('');
  const [compressedSize, setCompressedSize] = useState<string>('0');
  const [orignalMetaData, setOrignalMetaData] = useState<ImageMetaData>();
  const [compressedMetaData, setCompressedMetaData] = useState<ImageMetaData>();

  // Driven off the URIs so every path that produces an image (gallery, camera, remote, camera
  // roll) reports its metadata without each handler having to remember to ask for it.
  useEffect(() => {
    if (!orignalUri) return;
    getImageMetaData(orignalUri)
      .then(setOrignalMetaData)
      .catch((error) => console.log({ orignalMetaDataError: error }));
  }, [orignalUri]);

  useEffect(() => {
    if (!commpressedUri) return;
    getImageMetaData(commpressedUri)
      .then(setCompressedMetaData)
      .catch((error) => console.log({ compressedMetaDataError: error }));
  }, [commpressedUri]);

  const compressHandler = async (result: ImagePicker.ImagePickerResponse) => {
    if (result.didCancel) {
      Alert.alert('Failed selecting Image');
      return;
    }
    if (result.assets) {
      const source: any = result.assets[0];
      if (source) {
        setOrignalSize(prettyBytes(source.fileSize || 0));

        setFileName(source.fileName);
        setMimeType(source.type);
        setOrignalUri(source.uri);
      }
      Image.compress(source.uri)
        .then(async (compressedFileUri) => {
          console.log(compressedFileUri, 'compressedFileUri');

          setCommpressedUri(compressedFileUri);
          const detail: any = await getFileInfo(compressedFileUri);
          setCompressedSize(prettyBytes(parseInt(detail.size || 0, 10)));
        })
        .catch((e) => {
          console.log(e, 'error');
        });
    }
  };

  const chooseCameraImageHandler = async () => {
    ImagePicker.launchCamera({
      mediaType: 'photo',
    })
      .then((result: ImagePicker.ImagePickerResponse) => {
        compressHandler(result);
      })
      .catch((err) => {
        console.log(err, 'error');
      });
  };

  const chooseGalleryImageHandler = async () => {
    ImagePicker.launchImageLibrary(
      {
        mediaType: 'photo',
      },
      (result: ImagePicker.ImagePickerResponse) => {
        compressHandler(result);
      },
    ).catch((err) => {
      console.log(err, 'error');
    });
  };

  const onPressRemoteImage = async () => {
    // const url ='https://img.freepik.com/free-photo/people-making-hands-heart-shape-silhouette-sunset_53876-15987.jpg';
    // const url = 'https://sample-videos.com/img/Sample-jpg-image-5mb.jpg';
    const url =
      'https://svs.gsfc.nasa.gov/vis/a030000/a030800/a030877/frames/5760x3240_16x9_01p/BlackMarble_2016_1200m_africa_s_labeled.png';
    setFileName('test.jpg');
    const size = await getFileSize(url);
    setOrignalSize(prettyBytes(parseInt(size, 10)));
    setMimeType('image/jpeg');
    Image.compress(url, {
      progressDivider: 10,
      downloadProgress: (progress) => {
        console.log('downloadProgress: ', progress);
        progressRef.current?.setProgress(progress);
      },
    })
      .then(async (compressedFileUri) => {
        console.log('test', commpressedUri);
        setCommpressedUri(compressedFileUri);
        const detail: any = await getFileInfo(compressedFileUri);
        setCompressedSize(prettyBytes(parseInt(detail.size || 0, 10)));
      })
      .catch((e) => {
        console.log(e, 'error1');
      });
  };

  const onCompressImagefromCameraoll = async () => {
    const photos = await CameraRoll.getPhotos({
      first: 1,
      assetType: 'Photos',
    });
    const phUrl: any = photos.page_info.end_cursor;
    Image.compress(phUrl, {
      compressionMethod: 'auto',
    })
      .then(async (compressedFileUri) => {
        setCommpressedUri(compressedFileUri);
        const detail: any = await getFileInfo(compressedFileUri);
        setCompressedSize(prettyBytes(parseInt(detail.size, 10)));
      })
      .catch((e) => {
        console.log(e, 'error');
      });
  };

  return (
    <View style={styles.container}>
      <ProgressBar ref={progressRef} />
      <View style={styles.imageContainer}>
        {orignalUri && (
          <RNImage
            resizeMode="contain"
            source={{ uri: orignalUri }}
            style={{
              width: dimension.width / 3,
            }}
          />
        )}
        {commpressedUri && (
          <RNImage
            resizeMode="contain"
            source={{ uri: commpressedUri }}
            style={{
              width: dimension.width / 3,
            }}
          />
        )}
      </View>
      <ScrollView style={styles.infoScroll} contentContainerStyle={styles.scrollContent}>
        <Row label="File Name" value={fileName} />
        <Row label="Mime Type" value={mimeType} />
        <Row label="Orignal Size" value={orignalSize} />
        <Row label="Compressed Size" value={compressedSize} />
        <View style={styles.metaDataRow}>
          <View style={styles.metaDataColumn}>
            <MetaDataList title="Orignal (getImageMetaData)" data={orignalMetaData} order={IMAGE_FIELD_ORDER} />
          </View>
          <View style={styles.metaDataColumn}>
            <MetaDataList title="Compressed (getImageMetaData)" data={compressedMetaData} order={IMAGE_FIELD_ORDER} />
          </View>
        </View>
        <Button onPress={chooseGalleryImageHandler} title="Choose Image (Gallery)" />
        <Button onPress={chooseCameraImageHandler} title="Choose Image (Camera)" />
        <Button onPress={onPressRemoteImage} title="Remote Image (http://)" />
        {Platform.OS === 'ios' && <Button title={'compress image from camera roll (ph://)'} onPress={onCompressImagefromCameraoll} />}
      </ScrollView>
    </View>
  );
};

export default Index;

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  imageContainer: {
    flex: 1,
    width: '100%',
    flexDirection: 'row',
    justifyContent: 'space-around',
  },
  infoScroll: {
    flex: 1,
    width: '100%',
  },
  // A ScrollView rejects child-layout props on `style`, so the centring the old View did lives
  // here. `flexGrow` keeps short content centred while still letting long content scroll.
  scrollContent: {
    flexGrow: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingBottom: 24,
  },
  metaDataRow: {
    width: '100%',
    flexDirection: 'row',
    paddingHorizontal: 15,
  },
  metaDataColumn: {
    flex: 1,
    paddingRight: 8,
  },
});
