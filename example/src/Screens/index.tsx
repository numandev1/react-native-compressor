import React from 'react';
import { createNativeStackNavigator } from 'react-native-screens/native-stack';
import MainScreen from './Main';
import ImageScreen from './Image';
import AudioScreen from './Audio';
import VideoScreen from './Video';
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
      {Object.keys(SCREENS).map((name) => (
        <ThemeNavStack.Screen
          key={name}
          name={name}
          getComponent={() => SCREENS[name].screen}
          options={{ title: SCREENS[name].title || name }}
        />
      ))}
    </ThemeNavStack.Navigator>
  );
}

export default ThemeStack;
