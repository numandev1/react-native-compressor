import { useRouter } from 'expo-router';
import { ReactElement } from 'react';
import { FlatList, StyleSheet, Text, Pressable, ScrollView, View } from 'react-native';

const SCREENS = [
  { key: 'image', title: 'Image Screen' },
  { key: 'audio', title: 'Audio Screen' },
  { key: 'video', title: 'Video Screen' },
  { key: 'strip-audio', title: 'Strip Audio Screen' },
  { key: 'util', title: 'Util Screen' },
] as const;

const ItemSeparator = (): ReactElement => {
  return <View style={styles.separator} />;
};

export default function HomeScreen() {
  const router = useRouter();
  return (
    <FlatList
      style={styles.list}
      data={SCREENS}
      ItemSeparatorComponent={ItemSeparator}
      renderItem={({ item }) => (
        <Pressable
          style={({ pressed }) => [{ opacity: pressed ? 0.5 : 1 }, styles.button]}
          onPress={() => router.push(`/${item.key}` as any)}>
          <Text style={styles.buttonText}>{item.title}</Text>
        </Pressable>
      )}
      renderScrollComponent={(props) => <ScrollView {...props} />}
    />
  );
}

const styles = StyleSheet.create({
  list: {
    backgroundColor: '#EFEFF4',
  },
  separator: {
    height: 1,
    backgroundColor: '#DBDBE0',
  },
  buttonText: {
    backgroundColor: 'transparent',
  },
  button: {
    flex: 1,
    height: 60,
    padding: 10,
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#fff',
  },
});
