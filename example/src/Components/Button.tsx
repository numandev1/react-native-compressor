import React from 'react';
import { StyleSheet, Text, Pressable } from 'react-native';

function Button({ onPress, title }: { onPress: () => void; title: string }) {
  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [
        {
          opacity: pressed ? 0.7 : 1,
        },
        styles.button,
      ]}
    >
      <Text style={styles.text}>{title}</Text>
    </Pressable>
  );
}

export default Button;
const styles = StyleSheet.create({
  text: {
    fontSize: 20,
    color: 'white',
  },
  button: {
    padding: 10,
    marginBottom: 5,
    backgroundColor: 'green',
  },
});
