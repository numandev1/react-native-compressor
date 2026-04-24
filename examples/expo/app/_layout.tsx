import { Stack } from 'expo-router';

export default function RootLayout() {
  return (
    <Stack>
      <Stack.Screen name="index" options={{ title: 'Compressor Examples' }} />
      <Stack.Screen name="image" options={{ title: 'Image Screen' }} />
      <Stack.Screen name="audio" options={{ title: 'Audio Screen' }} />
      <Stack.Screen name="video" options={{ title: 'Video Screen' }} />
      <Stack.Screen name="util" options={{ title: 'Util Screen' }} />
    </Stack>
  );
}
