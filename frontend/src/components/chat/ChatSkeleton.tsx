export function ChatSkeleton() {
  return (
    <div className="flex h-full">
      {/* 侧边栏骨架 */}
      <div className="hidden w-[260px] border-r border-[#E5E7EB] bg-[#F9FAFB] p-4 md:block">
        <div className="mb-4 h-10 w-full animate-pulse rounded-lg bg-[#E5E7EB]" />
        <div className="space-y-2">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="h-10 w-full animate-pulse rounded-lg bg-[#E5E7EB]" />
          ))}
        </div>
      </div>
      {/* 主内容区骨架 */}
      <div className="flex flex-1 flex-col items-center justify-center">
        <div className="h-8 w-48 animate-pulse rounded bg-[#E5E7EB]" />
        <div className="mt-4 h-4 w-64 animate-pulse rounded bg-[#E5E7EB]" />
      </div>
    </div>
  );
}