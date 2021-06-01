import React, { useEffect } from 'react';
import { View, Text, StyleSheet } from 'react-native';
import compressor, { Image } from 'react-native-compressor';
const Index = () => {
  useEffect(() => {
    Image.compress('file://bowling-alleys/the-dude.jpg', {
      maxWidth: 1000,
    })
      .then(() => {})
      .catch((e) => {
        console.log(e, 'error');
      });
    console.log(compressor, 'compressor', Image);
  }, []);
  return (
    <View style={styles.container}>
      <Text>Image Screen</Text>
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
});
