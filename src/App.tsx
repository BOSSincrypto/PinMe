import { Toaster } from "@/components/ui/toaster";
import { Toaster as Sonner } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import { lazy, Suspense, useEffect } from "react";
import { BottomNav } from "./components/BottomNav";

const queryClient = new QueryClient();
const ContactList = lazy(() => import("./pages/ContactList"));
const AddContact = lazy(() => import("./pages/AddContact"));
const ContactDetail = lazy(() => import("./pages/ContactDetail"));
const Dates = lazy(() => import("./pages/Dates"));
const Reminders = lazy(() => import("./pages/Reminders"));
const Settings = lazy(() => import("./pages/Settings"));
const NotFound = lazy(() => import("./pages/NotFound"));

const App = () => {
  useEffect(() => {
    document.documentElement.classList.add('dark');
  }, []);

  return (
    <QueryClientProvider client={queryClient}>
      <TooltipProvider>
        <Toaster />
        <Sonner />
        <BrowserRouter
          future={{
            v7_startTransition: true,
            v7_relativeSplatPath: true,
          }}
        >
          <Suspense fallback={<div className="p-6 text-center">Загрузка…</div>}>
            <Routes>
              <Route path="/" element={<ContactList />} />
              <Route path="/add-contact" element={<AddContact />} />
              <Route path="/contact/:id" element={<ContactDetail />} />
              <Route path="/reminders" element={<Reminders />} />
              <Route path="/dates" element={<Dates />} />
              <Route path="/settings" element={<Settings />} />
              <Route path="*" element={<NotFound />} />
            </Routes>
          </Suspense>
          <BottomNav />
        </BrowserRouter>
      </TooltipProvider>
    </QueryClientProvider>
  );
};

export default App;
