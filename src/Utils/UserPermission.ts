// 定义用户权限常量
export const UserPermission = {
    USER: 1, // 会员
    UPPER: 1 << 1, // 收录员
    CREATOR: 1 << 2, // 创作者
} as const;

// 权限名称映射
const PERMISSION_NAMES: Record<number, string> = {
    1: "会员",
    2: "收录员",
    4: "创作者",
};

// 根据权限值获取中文名称数组
export const cnByUserPermission = (permission: number): Array<string> => {
    const result: Array<string> = [];
    // 遍历所有已知权限
    for (const [bit, name] of Object.entries(PERMISSION_NAMES)) {
        const bitNum = parseInt(bit);
        // 使用按位与检查是否拥有该权限
        if ((permission & bitNum) === bitNum) {
            result.push(name);
        }
    }
    return result;
};

// 获取所有用户权限选项
export const getUserPermissionOptions = (): Array<{value: number; label: string}> => {
    return Object.entries(PERMISSION_NAMES).map(([value, label]) => ({value: parseInt(value), label: label}));
}