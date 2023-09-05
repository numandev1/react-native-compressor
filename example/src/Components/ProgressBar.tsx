import React from 'react';
import { forwardRef, useImperativeHandle } from 'react';
import { View, StyleSheet } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
} from 'react-native-reanimated';

type Props = {};

export type ProgressBarRafType = {
  setProgress: (percentage: number) => void;
};

const ProgressBar = forwardRef(({}: Props, ref) => {
  const sharedProgress = useSharedValue(0);

  const styleAnimated = useAnimatedStyle(() => {
    return {
      width: `${sharedProgress.value * 100}%`,
    };
  });

  useImperativeHandle(ref, () => ({
    setProgress: (percentage: number) => {
      sharedProgress.value = percentage;
    },
  }));

  return (
    <View style={styles.track}>
      <Animated.View style={[styles.progress, styleAnimated]} />
    </View>
  );
});

export default ProgressBar;

const styles = StyleSheet.create({
  track: {
    height: 8,
    width: '100%',
    borderRadius: 8,
    backgroundColor: '#505059',
  },
  progress: {
    height: 8,
    backgroundColor: '#00B37E',
    borderRadius: 8,
  },
});
