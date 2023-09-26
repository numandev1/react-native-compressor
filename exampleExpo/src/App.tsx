import * as React from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { enableScreens } from 'react-native-screens';
import Screens from './Screens';
enableScreens();
const MainStack = createNativeStackNavigator();
export default function App() {
  return (
    <NavigationContainer>
      <MainStack.Navigator>
        <MainStack.Screen
          name="main_screen"
          component={Screens}
          options={{
            headerShown: false,
          }}
        />
      </MainStack.Navigator>
    </NavigationContainer>
  );
}
