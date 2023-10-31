import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import MainScreen from './Main';
import ImageScreen from './Image';
import AudioScreen from './Audio';
import VideoScreen from './Video';
import UtilScreen from './Util';
export type Screens = Record<
  string,
  { screen: React.ComponentType; title?: string }
>;

export const SCREENS: Screens = {
  image_screen: {
    screen: ImageScreen,
    title: 'Image Screen',
  },
  audio_screen: {
    screen: AudioScreen,
    title: 'Audio Screen',
  },
  video_screen: {
    screen: VideoScreen,
    title: 'Video Screen',
  },
  util_screen: {
    screen: UtilScreen,
    title: 'Util Screen',
  },
};

const ThemeNavStack = createNativeStackNavigator();
function ThemeStack() {
  return (
    <ThemeNavStack.Navigator>
      <ThemeNavStack.Screen
        name="main_screen"
        options={{ title: 'Compressor Examples' }}
        component={MainScreen}
      />
      {Object.entries(SCREENS).map(([name, component]) => (
        <ThemeNavStack.Screen
          key={name}
          name={name}
          getComponent={() => component.screen}
          options={{ title: component.title || name }}
        />
      ))}
    </ThemeNavStack.Navigator>
  );
}

export default ThemeStack;
