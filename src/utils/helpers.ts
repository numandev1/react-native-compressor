export const uuidv4 = () => {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
    const r =
        (parseFloat(
          '0.' +
            Math.random().toString().replace('0.', '') +
            new Date().getTime()
        ) *
          16) |
        0,
      // eslint-disable-next-line eqeqeq
      v = c == 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
};
