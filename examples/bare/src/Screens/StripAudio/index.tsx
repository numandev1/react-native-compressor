import { useState, useRef, useCallback } from 'react';
import {
  View,
  Text,
  Button,
  Alert,
  Platform,
  ScrollView,
  StyleSheet,
} from 'react-native';
import { Video, getRealPath } from 'react-native-compressor';
import VideoPlayer from 'react-native-video';
import * as ImagePicker from 'react-native-image-picker';
import prettyBytes from 'pretty-bytes';
import { getFileInfo } from '../../Utils';
import ProgressBar from '../../Components/ProgressBar';
import type { ProgressBarRafType } from '../../Components/ProgressBar';

export default function StripAudioScreen() {
  const progressRef = useRef<ProgressBarRafType>(null);
  const cancellationIdRef = useRef<string>('');

  const [sourceVideo, setSourceVideo] = useState<string>();
  const [sourceSize, setSourceSize] = useState<string>();
  const [compressedVideo, setCompressedVideo] = useState<string>();
  const [compressedSize, setCompressedSize] = useState<string>();
  const [isCompressing, setIsCompressing] = useState(false);
  const [playerKey, setPlayerKey] = useState(0);

  const selectVideo = async () => {
    try {
      ImagePicker.launchImageLibrary(
        { mediaType: 'video' },
        async (result: ImagePicker.ImagePickerResponse) => {
          if (result.didCancel) return;
          if (result.errorCode) {
            Alert.alert('Failed selecting video');
            return;
          }
          if (result.assets) {
            let uri = result.assets[0]?.uri;
            if (!uri) return;
            if (Platform.OS === 'android' && uri.includes('content://')) {
              const realPath = await getRealPath(uri, 'video');
              uri = 'file://' + realPath;
              console.log('realPath ==>', realPath);
            }
            setSourceVideo(uri);
            setCompressedVideo(undefined);
            setCompressedSize(undefined);
            setPlayerKey((k) => k + 1);
            const detail: any = await getFileInfo(uri);
            setSourceSize(prettyBytes(parseInt(detail.size, 10)));
          }
        },
      );
    } catch (error) {
      console.log('Failed to select video', error);
    }
  };

  const compressWithStripAudio = async () => {
    if (!sourceVideo) return;
    setIsCompressing(true);
    try {
      const dstUrl = await Video.compress(
        sourceVideo,
        {
          compressionMethod: 'auto',
          progressDivider: 10,
          stripAudio: true,
          getCancellationId: (id) => (cancellationIdRef.current = id),
        },
        (progress) => {
          console.log('Compression Progress: ', progress);
          progressRef.current?.setProgress(progress);
        },
      );
      console.log('Compressed (stripAudio):', dstUrl);
      setCompressedVideo(dstUrl);
      setPlayerKey((k) => k + 1);
      const detail: any = await getFileInfo(dstUrl);
      setCompressedSize(prettyBytes(parseInt(detail.size, 10)));
      progressRef.current?.setProgress(0);
    } catch (error) {
      console.log('Compression error:', error);
      Alert.alert('Compression Failed', String(error));
      progressRef.current?.setProgress(0);
    } finally {
      setIsCompressing(false);
    }
  };

  const compressWithAudio = async () => {
    if (!sourceVideo) return;
    setIsCompressing(true);
    try {
      const dstUrl = await Video.compress(
        sourceVideo,
        {
          compressionMethod: 'auto',
          progressDivider: 10,
          getCancellationId: (id) => (cancellationIdRef.current = id),
        },
        (progress) => {
          console.log('Compression Progress: ', progress);
          progressRef.current?.setProgress(progress);
        },
      );
      console.log('Compressed (with audio):', dstUrl);
      setCompressedVideo(dstUrl);
      setPlayerKey((k) => k + 1);
      const detail: any = await getFileInfo(dstUrl);
      setCompressedSize(prettyBytes(parseInt(detail.size, 10)));
      progressRef.current?.setProgress(0);
    } catch (error) {
      console.log('Compression error:', error);
      Alert.alert('Compression Failed', String(error));
      progressRef.current?.setProgress(0);
    } finally {
      setIsCompressing(false);
    }
  };

  const cancelCompression = () => {
    Video.cancelCompression(cancellationIdRef.current);
  };

  const onVideoError = useCallback((error: any) => {
    console.log('Video playback error:', JSON.stringify(error));
  }, []);

  const onVideoLoad = useCallback((data: any) => {
    console.log('Video loaded:', JSON.stringify(data));
  }, []);

  return (
    <View style={styles.container}>
      <ProgressBar ref={progressRef} />
      <ScrollView style={styles.scrollContent} contentContainerStyle={styles.scrollContainer}>
        <View style={styles.section}>
          <Button title="Select Video" onPress={selectVideo} />
        </View>

        {sourceVideo && (
          <>
            <View style={styles.section}>
              <Text style={styles.label}>Source Video {sourceSize && `(${sourceSize})`}</Text>
              <View style={styles.videoContainer}>
                <VideoPlayer
                  key={`source-${playerKey}`}
                  source={{ uri: sourceVideo }}
                  style={styles.video}
                  controls={true}
                  paused={true}
                  resizeMode="contain"
                  onError={onVideoError}
                  onLoad={onVideoLoad}
                />
              </View>
            </View>

            <View style={styles.buttonRow}>
              <View style={styles.buttonWrapper}>
                <Button
                  title="Strip Audio"
                  onPress={compressWithStripAudio}
                  disabled={isCompressing}
                />
              </View>
              <View style={styles.buttonWrapper}>
                <Button
                  title="Keep Audio"
                  onPress={compressWithAudio}
                  disabled={isCompressing}
                />
              </View>
            </View>

            {isCompressing && (
              <View style={styles.section}>
                <Button title="Cancel" onPress={cancelCompression} color="red" />
              </View>
            )}
          </>
        )}

        {compressedVideo && (
          <View style={styles.section}>
            <Text style={styles.label}>
              Compressed Video {compressedSize && `(${compressedSize})`}
            </Text>
            <Text style={styles.hint}>
              Play to verify - stripped audio should have no sound
            </Text>
            <View style={styles.videoContainer}>
              <VideoPlayer
                key={`compressed-${playerKey}`}
                source={{ uri: compressedVideo }}
                style={styles.video}
                controls={true}
                paused={true}
                resizeMode="contain"
                onError={onVideoError}
                onLoad={onVideoLoad}
              />
            </View>
          </View>
        )}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  scrollContent: {
    flex: 1,
  },
  scrollContainer: {
    paddingBottom: 40,
  },
  section: {
    padding: 12,
  },
  label: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 8,
  },
  hint: {
    fontSize: 12,
    color: '#666',
    marginBottom: 8,
  },
  videoContainer: {
    width: '100%',
    height: 220,
    backgroundColor: '#000',
    borderRadius: 8,
    overflow: 'hidden',
  },
  video: {
    width: '100%',
    height: '100%',
  },
  buttonRow: {
    flexDirection: 'row',
    paddingHorizontal: 12,
    gap: 8,
  },
  buttonWrapper: {
    flex: 1,
  },
});
